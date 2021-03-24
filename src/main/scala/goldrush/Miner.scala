package goldrush

import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Parallel}
import goldrush.Constants._
import goldrush.Licenser.{Issuer, LicenseId, Licenser}
import goldrush.Miner._
import monix.catnap.ConcurrentQueue
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.{Observable, OverflowStrategy}

class Miner[F[_]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift](
    goldStore: GoldStore[F],
    licenser: Observable[LicenseId],
    client: Client[F]
) {
  def mine: F[Unit] = {
    val digger = {
      explorator(client.explore)(Area(0, 0, 3500, 3500), 490_000)
        .asyncBoundary(OverflowStrategy.Unbounded)
        .mapParallelUnorderedF(digParallelism) { nextArea =>
          val (x, y, amount) = nextArea

          def dig(
              level: Int,
              foundTreasures: Seq[String]
          ): Observable[Seq[String]] = {
            for {
              license <- licenser
              _ = Counters.digsCount.incrementAndGet()
              newTreasures <- Observable.from(client.dig(license, x, y, level))
              nextTreasures = foundTreasures ++ newTreasures
              goDeeper = level < 10 && nextTreasures.size < amount
              result <-
                if (goDeeper)
                  dig(level + 1, nextTreasures)
                else {
                  Counters.foundTreasuresCount.incrementAndGet()
                  Observable.pure(nextTreasures)
                }
            } yield result
          }

          dig(1, Seq.empty).toListL.map(_.flatten)
        }
        .asyncBoundary(OverflowStrategy.Unbounded)
        .flatMap(Observable.fromIterable)

    }

    val coins = digger
      .mapParallelUnorderedF(cashParallelism) { treasure =>
        client.cash(treasure)
      }
//      .mapEvalF(c => goldStore.put(c: _*).as(c))
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

  def apply[F[_]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift](
      client: Client[F]
  ): Resource[F, Miner[F]] = {
    for {
      goldStore <- Resource.liftF(GoldStoreImpl[F](goldStoreSize))
      licenser = Licenser.applyObserve(licenceParallelism, Issuer.free(client))
      miner = new Miner[F](goldStore, licenser, client)
    } yield miner
  }

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
            .asyncBoundary(OverflowStrategy.Unbounded)
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
      .asyncBoundary(OverflowStrategy.Unbounded)
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

  def exploratorResource[F[_]: TaskLike: Applicative: Concurrent: TaskLift: ContextShift](
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Resource[F, F[(X, Y, Amount)]] = {
    for {
      queue <- Resource.liftF(ConcurrentQueue.unbounded[F, (X, Y, Amount)]())
      _ <- Concurrent[F].background {
        exploratorBatched(maxExploreArea)(explore)(area, amount)
          .mapEvalF { x =>
            queue.offer(x)
          }
          .completedF[F]
      }
    } yield queue.poll
  }

}
