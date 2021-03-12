package goldrush

import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.applicative._
import cats.syntax.functor._
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
          (result.area.posX, result.area.posY, result.amount)
        }
    }

    val digger = {
      val seed = (1 -> Seq.empty[String]).pure[F]

      Iterant.fromResource(licensesR).flatMap { licenses =>
        explorator
          .mapEval { case (x, y, amount) =>
            licenses.foldWhileLeftEvalL(seed) {
              case ((level, foundTreasures), license) =>
                client.dig(license, x, y, level).map { newTreasures =>
                  val nextTreasures = newTreasures ++ foundTreasures
                  val result = level + 1 -> nextTreasures
                  Either.cond(
                    level >= 10 || nextTreasures.size >= amount,
                    result,
                    result
                  )
                }
            }
          }
          .mapBatch { case (_, treasures) =>
            Batch.fromSeq(treasures)
          }
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
