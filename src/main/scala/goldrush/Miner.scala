package goldrush

import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.parallel._
import cats.{Applicative, Parallel}
import monix.catnap.ConcurrentQueue
import monix.tail.Iterant
import monix.tail.batches.Batch

case class Miner[F[_]: Sync: Parallel: Applicative: Concurrent: ContextShift](
    client: Client[F]
) {
  def mine: F[Int] = {
    val licensesR: Resource[F, Iterant[F, Int]] = {
      for {
        queue <- Resource.liftF(ConcurrentQueue.bounded[F, Int](3))
        _ <- Concurrent[F].background {
          Iterant[F]
            .repeatEvalF(client.issueLicense())
            .mapEval { license =>
              val licensesUses =
                Seq.fill(license.digAllowed - license.digUsed)(license.id)
              queue.offerMany(licensesUses)
            }
            .completedL
        }
      } yield Iterant[F].repeatEvalF(queue.poll)
    }

    val explorator = {
      val sideSize = 3500
      val side = Iterant[F].fromIterable(0 until sideSize)
      val coords = side.flatMap(x => side.flatMap(y => Iterant.pure(x, y)))

      coords
        .mapEval { case (x, y) =>
          client.explore(Area(x, y, 1, 1))
        }
        .filter(_.amount > 0)
        .map { result =>
          (result.area, result.amount)
        }
    }

    val digger = {
      Iterant.fromResource(licensesR).flatMap { licenses =>
        explorator
          .mapEval { case (area, amount) =>
            for {
              foundTreasures <- Ref[F].of(Seq.empty[String])
              _ <- area.locations.parTraverse { case (x, y) =>
                licenses.foldWhileLeftEvalL(1.pure) { case (level, license) =>
                  for {
                    newTreasures <- client.dig(license, x, y, level)
                    nextTreasures <- foundTreasures
                      .getAndUpdate(newTreasures ++ _)
                    result = level + 1
                  } yield Either.cond(
                    level >= 10 || nextTreasures.size >= amount,
                    result,
                    result
                  )
                }
              }
              result <- foundTreasures.get
            } yield result
          }
          .mapBatch(a => Batch.fromSeq(a))
      }
    }

    val coins = digger
      .mapEval { treasure =>
        client.cash(treasure)
      }
      .mapBatch(coins => Batch.fromSeq(coins))

    coins.sumL
  }
}
