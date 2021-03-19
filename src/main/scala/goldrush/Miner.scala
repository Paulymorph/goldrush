package goldrush

import cats.effect.concurrent.MVar
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Parallel}
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.Observable

case class Miner[F[
    _
]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift](
    client: Client[F]
) {
  def mine: F[Int] = {
    val digParallelism = 16

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

    val explorator = {
      val sideSize = 3500
      val step = 3
      val side = Observable.range(0, sideSize, step).map(_.toInt)
      val coords = side.flatMap(x => side.flatMap(y => Observable.pure(x, y)))

      coords
        .mapParallelUnorderedF(4) { case (x, y) =>
          client.explore(
            Area(
              x,
              y,
              Math.min(step, sideSize - x),
              Math.min(step, sideSize - y)
            )
          )
        }
        .filter(_.amount > 0)
        .map { result =>
          (result.area, result.amount)
        }
        .flatMapIterable { case (area, _) =>
          area.locations
        }
        .mapParallelUnorderedF(8) { case (x, y) =>
          client.explore(Area(x, y, 1, 1))
        }
        .filter(_.amount > 0)
        .map { result =>
          (result.area.posX, result.area.posY, result.amount)
        }
    }

    val digger = {
      Observable
        .fromResource(licensesR)
        .flatMap { nextLicense =>
          explorator
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
      .mapParallelUnorderedF(8) { treasure =>
        client.cash(treasure)
      }
      .flatMap(Observable.fromIterable)

    TaskLift[F].apply(coins.sumL)
  }
}
