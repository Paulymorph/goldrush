package goldrush

import cats.effect.concurrent.MVar
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Parallel}
import goldrush.Miner._
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.{Observable, OverflowStrategy}
import Math.min

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

  def explorator[F[_]: TaskLike](
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    val Area(x, y, sizeX, sizeY) = area
    if (sizeX * sizeY == 1)
      Observable((x, y, amount))
    else {
      val subAreas =
        if (sizeX > sizeY) {
          val step = sizeX / 2
          val untilX = x + sizeX
          Observable
            .range(x, untilX, step)
            .map(_.toInt)
            .map(newX => Area(newX, y, min(step, untilX - newX), sizeY))
        } else {
          val step = sizeY / 2
          val untilY = y + sizeY
          Observable
            .range(y, untilY, step)
            .map(_.toInt)
            .map(newY => Area(x, newY, sizeX, min(step, untilY - newY)))
        }

      subAreas
        .mapEvalF(explore)
        .filter(_.amount > 0)
        .mergeMap { exploreResult =>
          explorator(explore)(exploreResult.area, exploreResult.amount)
        }
    }
  }
}
