package goldrush

import cats.effect.concurrent.MVar
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Parallel}
import goldrush.Constants._
import goldrush.Miner._
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.{Observable, OverflowStrategy}

case class Miner[F[
    _
]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift](
    client: Client[F]
) {
  def mine: F[Unit] = {

    val licensesR: Resource[F, F[Int]] = {
      for {
        queue <- Resource.liftF(MVar.empty[F, Int])
        _ <- Concurrent[F].background {
          Observable
            .repeat(())
            .mapParallelUnorderedF(licenceParallelism)(_ => client.issueLicense())
            .flatMapIterable { license =>
              Seq.fill(license.digAllowed - license.digUsed)(license.id)
            }
            .mapEvalF { x =>
              Counters.getLicenceCount.incrementAndGet()
              queue.put(x)
            }
            .completedF[F]
        }
      } yield queue.take
    }

    val digger = {
      Observable
        .fromResource(licensesR)
        .flatMap { nextLicense =>
          Observable
            .fromResource(exploratorResource(client.explore)(Area(0, 0, 3500, 3500), 490_000))
            .flatMap { nextAreaF => Observable.repeatEvalF(nextAreaF) }
            .asyncBoundary(OverflowStrategy.Unbounded)
            .mapParallelUnorderedF(digParallelism) { nextArea =>
              val (x, y, amount) = nextArea
              def dig(
                  level: Int,
                  foundTreasures: Seq[String]
              ): F[Seq[String]] = {
                for {
                  license <- nextLicense
                  _ = Counters.digsCount.incrementAndGet()
                  newTreasures <- client.dig(license, x, y, level)
                  nextTreasures = foundTreasures ++ newTreasures
                  goDeeper = level < 10 && nextTreasures.size < amount
                  result <-
                    if (goDeeper)
                      dig(level + 1, nextTreasures)
                    else {
                      Counters.foundTreasuresCount.incrementAndGet()
                      Applicative[F].pure(nextTreasures)
                    }
                } yield result
              }

              dig(1, Seq.empty)
            }
        }
        .flatMap(Observable.fromIterable)
    }

    val coins = digger
      .mapParallelUnorderedF(cashParallelism) { treasure =>
        client.cash(treasure)
      }
      .foreachL { x =>
        val c = Counters.cashesCount.incrementAndGet()
        Counters.cashesSum.addAndGet(x.size)
        if (c % 250 == 0) {
          Counters.print()
        } else ()
      }

    TaskLift[F].apply(coins)
  }
}

object Miner {
  type X = Int
  type Y = Int
  type Amount = Int
  type Explorator = Observable[(X, Y, Amount)]

  def exploratorBinary[F[_]: TaskLike](
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    val Area(x, y, sizeX, sizeY) = area
    if (amount == 0 || sizeX * sizeY < 1) Observable.empty
    else if (sizeX * sizeY == 1) {
      Counters.foundCellsCount.incrementAndGet()
      Observable((x, y, amount))
    } else {
      val (left, right) =
        if (sizeX > sizeY) {
          val step = sizeX / 2
          val left = Area(x, y, step, sizeY)
          val right = Area(x + step, y, sizeX - step, sizeY)
          (left, right)
        } else {
          val step = sizeY / 2
          val left = Area(x, y, sizeX, step)
          val right = Area(x, y + step, sizeX, sizeY - step)
          (left, right)
        }

      val exploreadAreas = Observable
        .from(explore(left))
        .flatMapIterable { explored =>
          Seq(explored, ExploreResponse(right, amount - explored.amount))
        }

      exploreadAreas
        .filter(_.amount > 0)
        .flatMap { explored =>
          exploratorBinary(explore)(explored.area, explored.amount)
        }
    }
  }

  def exploratorBatched[F[_]: TaskLike: Applicative](maxStep: Int)(
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    import area._
    val xs = Observable.range(posX, posX + sizeX, maxStep).map(_.toInt)
    val ys = Observable.range(posY, posY + sizeY, maxStep).map(_.toInt)
    val coords = xs.flatMap(x => ys.flatMap(y => Observable.pure(x, y)))

    coords
      .mapParallelUnorderedF(exploreParallelism) { case (x, y) =>
        explore(
          Area(
            x,
            y,
            Math.min(maxStep, posX + sizeX - x),
            Math.min(maxStep, posY + sizeY - y)
          )
        )
      }
      .filter(_.amount > 0)
      .flatMap { response =>
        exploratorBinary(explore)(response.area, response.amount)
      }

  }

  def explorator[F[_]: TaskLike: Applicative](
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    exploratorBatched(maxExploreArea)(explore)(area, amount)
  }

  def exploratorResource[F[_]: TaskLike: Applicative: Concurrent: TaskLift](
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Resource[F, F[(X, Y, Amount)]] = {
    for {
      queue <- Resource.liftF(MVar.empty[F, (X, Y, Amount)])
      _ <- Concurrent[F].background {
        exploratorBatched(maxExploreArea)(explore)(area, amount)
          .completedF[F]
      }
    } yield queue.take
  }

}
