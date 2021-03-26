package goldrush

import cats.effect.concurrent.MVar
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Parallel}
import goldrush.Licenser.{Issuer, Licenser}
import goldrush.Miner._
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.Observable

class Miner[F[_]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift](
    goldStore: GoldStore[F],
    licenser: Licenser[F],
    client: Client[F],
    digParallelism: Int
) {
  def mine: F[Int] = {
    val digger = {
      explorator(client.explore)(Area(0, 0, 3500, 3500), 490_000)
        .mapParallelUnorderedF(digParallelism) { case (x, y, amount) =>
          def dig(
              level: Int,
              foundTreasures: Seq[String]
          ): F[Seq[String]] = {
            for {
              license <- licenser
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
        .flatMap(Observable.fromIterable)
    }

    val coins = digger
      .mapParallelUnorderedF(digParallelism) { treasure =>
        client.cash(treasure)
      }
      .flatMap(Observable.fromIterable)
      .mapEvalF(c => goldStore.put(c).as(c))

    TaskLift[F].apply(coins.sumL)
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
    val digParallelism = 36
    for {
      goldStore <- Resource.liftF(GoldStoreImpl[F](2000))
      licenser <- Resource.liftF(
        Licenser.noBackground(Issuer.paidRandom(1, client, goldStore))
      )
      miner = new Miner[F](goldStore, licenser, client, digParallelism)
    } yield miner
  }

  def exploratorBinary[F[_]: TaskLike](
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    val Area(x, y, sizeX, sizeY) = area
    if (amount == 0 || sizeX * sizeY < 1) Observable.empty
    else if (sizeX * sizeY == 1) Observable((x, y, amount))
    else {
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
