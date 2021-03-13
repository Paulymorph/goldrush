package goldrush

import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.{Applicative, Parallel}
import monix.catnap.ConcurrentQueue
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.Observable

case class Miner[F[
    _
]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift](
    client: Client[F]
) {
  def mine: F[Int] = {
    val licensesR: Resource[F, Observable[Int]] = {
      for {
        queue <- Resource.liftF(ConcurrentQueue.bounded[F, Int](3))
        _ <- Concurrent[F].background {
          Observable
            .repeatEvalF(client.issueLicense())
            .mapEvalF { license =>
              val licensesUses =
                Seq.fill(license.digAllowed - license.digUsed)(license.id)
              queue.offerMany(licensesUses)
            }
            .completedF[F]
        }
      } yield Observable.repeatEvalF(queue.poll)
    }

    val explorator = {
      val sideSize = 3500
      val side = Observable.fromIterable(0 until sideSize)
      val coords = side.flatMap(x => side.flatMap(y => Observable.pure(x, y)))

      coords
        .mapEvalF { case (x, y) =>
          client.explore(Area(x, y, 1, 1))
        }
        .filter(_.amount > 0)
        .map { result =>
          (result.area, result.amount)
        }
    }

    val digger = {
      Observable
        .fromResource(licensesR)
        .flatMap { licenses =>
          explorator
            .mapEvalF { case (area, amount) =>
              for {
                foundTreasures <- Ref[F].of(Seq.empty[String])
                _ <- area.locations.parTraverse { case (x, y) =>
                  val licenseF = TaskLift[F].apply(licenses.firstL)

                  def dig(level: Int): F[Unit] = {
                    for {
                      license <- licenseF
                      newTreasures <- client.dig(license, x, y, level)
                      nextTreasures <- foundTreasures
                        .getAndUpdate(_ ++ newTreasures)
                      goDeeper = level >= 10 || nextTreasures.size >= amount
                      _ <- if (goDeeper) dig(level + 1) else Applicative[F].unit
                    } yield ()
                  }

                  dig(1)
                }
                result <- foundTreasures.get
              } yield result
            }
        }
        .flatMap(Observable.fromIterable)
    }

    val coins = digger
      .mapEvalF { treasure =>
        client.cash(treasure)
      }
      .flatMap(Observable.fromIterable)

    TaskLift[F].apply(coins.sumL)
  }
}
