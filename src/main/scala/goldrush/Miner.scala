package goldrush

import cats.effect.concurrent.MVar
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Parallel}
import goldrush.Miner.{Amount, Explorator, X, Y}
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.{Observable, OverflowStrategy}

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

    def exploreBinary(area: Area, amount: Amount): Explorator = {
      if (area.sizeX == 1 && area.sizeY == 1)
        Observable((area.posX, area.posY, amount))
      else if (amount == 0 && area.sizeX * area.sizeY == 0)
        Observable.fromEither(
          Left(new IllegalArgumentException(s"$amount, $area"))
        )
      else {
        val (centerX, centerY) = (area.sizeX / 2, area.sizeY / 2)
        val subAreas = Seq(
          Area(area.posX, area.posY, centerX, centerY),
          Area(centerX + 1, area.posY, area.sizeX - centerX - 1, centerY),
          Area(area.posX, centerY + 1, centerX, area.sizeY - centerY - 1),
          Area(
            centerX + 1,
            centerY + 1,
            area.sizeX - centerX - 1,
            area.sizeY - centerY - 1
          )
        )
          .filter(ar => ar.sizeX * ar.sizeY > 0)

        Observable
          .fromIterable(subAreas)
          .mapParallelUnorderedF(digParallelism / 4)(client.explore)
          .filter(_.amount > 0)
          .flatMap(x => exploreBinary(x.area, x.amount))
      }
    }

    val explorator: Explorator = {
      val sideSize = 3500

      val wholeField = Area(0, 0, sideSize, sideSize)

      Observable
        .from(client.explore(wholeField))
        .flatMap(response => exploreBinary(response.area, response.amount))
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
}
