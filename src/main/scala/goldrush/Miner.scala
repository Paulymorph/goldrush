package goldrush

import cats.effect.concurrent.MVar
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Parallel}
import goldrush.Miner._
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.Observable

case class Miner[F[
    _
]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift](
    client: Client[F]
) {
  def mine: F[Int] = {
    val digParallelism = 36

    val licensesR: Resource[F, F[Int]] = {
      for {
        queue <- Resource.liftF(MVar.empty[F, Int])
        _ <- Concurrent[F].background {
          Observable
            .repeat(())
            .mapParallelUnorderedF(digParallelism)(_ => client.issueLicense())
            .flatMapIterable { license =>
              Seq.fill(license.digAllowed - license.digUsed)(license.id)
            }
            .mapEvalF(queue.put)
            .completedF[F]
        }
      } yield queue.take
    }

    val digger = {
      Observable
        .fromResource(licensesR)
        .flatMap { nextLicense =>
          explorator(client.explore)(Area(0, 0, 3500, 3500), 490_000)
            .mapParallelUnorderedF(digParallelism) { case (x, y, amount) =>
              def dig(
                  level: Int,
                  foundTreasures: Seq[String]
              ): F[Seq[String]] = {
                for {
                  license <- nextLicense
                  newTreasures <- client.dig(license, x, y, level)
                  nextTreasures = foundTreasures ++ newTreasures
                  goDeeper = level < 10 && nextTreasures.size < amount
                  result <-
                    if (goDeeper)
                      dig(level + 1, nextTreasures)
                    else Applicative[F].pure(nextTreasures)
                } yield result
              }

              dig(1, Seq.empty)
            }
        }
        .flatMap(Observable.fromIterable)
    }

    val coins = digger
      .mapParallelUnorderedF(digParallelism) { treasure =>
        client.cash(treasure)
      }
      .flatMap(Observable.fromIterable)

    TaskLift[F].apply(coins.sumL)
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
    else if (sizeX * sizeY == 1) Observable((x, y, amount))
    else {
      val subAreas =
        if (sizeX > sizeY) {
          val centerX = sizeX / 2
          (
            Area(x, y, centerX, sizeY),
            Area(x + centerX, y, sizeX - centerX, sizeY)
          )
        } else {
          val centerY = sizeY / 2
          (
            Area(x, y, sizeX, centerY),
            Area(x, y + centerY, sizeX, sizeY - centerY)
          )
        }

      val firstExplore = explore(subAreas._1)
      val nonEmptyAreas =
        Observable
          .from(firstExplore)
          .flatMap { firstResponse =>
            val secondAmount = amount - firstResponse.amount
            Observable(
              (firstResponse.area, firstResponse.amount),
              (subAreas._2, secondAmount)
            ).filter(_._2 > 0)
          }

      nonEmptyAreas
        .flatMap { case (area, amount) =>
          exploratorBinary(explore)(area, amount)
        }
    }
  }

  def exploratorBatched[F[_]: TaskLike: Applicative](maxStep: Int = 5)(
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    import area._
    val xs = Observable.range(posX, posX + sizeX, maxStep).map(_.toInt)
    val ys = Observable.range(posY, posY + sizeY, maxStep).map(_.toInt)
    val coords = xs.flatMap(x => ys.flatMap(y => Observable.pure(x, y)))

    coords
      .mapParallelUnorderedF(4) { case (x, y) =>
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
    exploratorBatched(5)(explore)(area, amount)
  }

}
