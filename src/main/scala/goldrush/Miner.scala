package goldrush

import java.util.{Collections, Comparator}
import java.util.concurrent.PriorityBlockingQueue

import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Parallel}
import goldrush.Licenser.{Issuer, Licenser}
import goldrush.Miner._
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.{Observable, OverflowStrategy}

class Miner[F[_]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift](
    goldStore: GoldStore[F],
    licenser: Licenser[F],
    client: Client[F],
    digParallelism: Int
) {
  def mine: F[Int] = {
    val prirityQ = new PriorityBlockingQueue[(String, Int)](
      50000,
      Collections.reverseOrder((o1: (String, Int), o2: (String, Int)) => o1._2.compareTo(o2._2))
    )

    val digger = {
      implicit val backPressure: OverflowStrategy[License] = OverflowStrategy.BackPressure(5000)
      explorator(client.explore)(Area(0, 0, 3500, 3500), 490_000)
        .mapParallelUnorderedF(digParallelism) { case (x, y, amount) =>
          def dig(
              level: Int,
              foundTreasures: Seq[(String, Int)]
          ): F[Seq[(String, Int)]] = {
            for {
              license <- licenser
              newTreasures <- client.dig(license, x, y, level)
              nextTreasures = foundTreasures ++ newTreasures.map(_ -> level)
              goDeeper = level < 10 && nextTreasures.size < amount
              result <-
                if (goDeeper)
                  dig(level + 1, nextTreasures)
                else Applicative[F].pure(nextTreasures)
            } yield result
          }

          dig(1, Seq.empty)
        }
        .asyncBoundary(OverflowStrategy.BackPressure(5000))
        .flatMap(Observable.fromIterable)
        .map { x =>
          prirityQ.add(x)
        }
        .asyncBoundary(OverflowStrategy.BackPressure(5000))
    }

    val coins = {
      implicit val backPressure: OverflowStrategy[License] = OverflowStrategy.BackPressure(5000)
      digger
        .mergeMap(_ =>
          Observable
            .repeatEval(prirityQ.take())
            .executeAsync
            .mapParallelUnorderedF(digParallelism) { treasure =>
              client.cash(treasure._1)
//          Applicative[F].pure(Seq.empty[Int])
            }
            .flatMap(Observable.fromIterable)
            .mapEvalF(c => goldStore.put(c).as(c))
        )
    }

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
    for {
      goldStore <- Resource.liftF(GoldStoreImpl[F](2000))
      licenser <- Licenser.apply(6, Issuer.paid(1, client, goldStore))
      miner = new Miner[F](goldStore, licenser, client, digParallelism = 24)
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

  def exploratorBatched[F[_]: TaskLike: Applicative](maxStepX: Int = 5, maxStepY: Int = 5)(
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    implicit val backPressure: OverflowStrategy[License] = OverflowStrategy.BackPressure(10)

    import area._
    val xs = Observable.range(posX, posX + sizeX, maxStepX).map(_.toInt)
    val ys = Observable.range(posY, posY + sizeY, maxStepY).map(_.toInt)
    val coords = xs.flatMap(x => ys.flatMap(y => Observable.pure(x, y)))

    val prirityQ = new PriorityBlockingQueue[ExploreResponse](
      1000,
      Collections.reverseOrder((o1: ExploreResponse, o2: ExploreResponse) =>
        o1.amount.compareTo(o2.amount)
      )
    )

    coords
      .mapParallelUnorderedF(10) { case (x, y) =>
        explore(
          Area(
            x,
            y,
            Math.min(maxStepX, posX + sizeX - x),
            Math.min(maxStepY, posY + sizeY - y)
          )
        )
      }
      .filter(_.amount > 0)
      .map { x =>
        prirityQ.add(x)
        x
      }
      .asyncBoundary(OverflowStrategy.BackPressure(500))
      .map(_ => prirityQ.take())
      .flatMap { response =>
        exploratorBinary(explore)(response.area, response.amount)
          .asyncBoundary(OverflowStrategy.BackPressure(50))
      }

  }

  def explorator[F[_]: TaskLike: Applicative](
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    exploratorBatched(120, maxStepY = 1)(explore)(area, amount)
  }

}
