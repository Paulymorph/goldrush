package goldrush

import cats.effect.Sync
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.{Applicative, Parallel}
import monix.tail.Iterant
import monix.tail.batches.Batch

case class Miner[F[_]: Sync: Parallel: Applicative](client: Client[F]) {
  def mine: F[Int] = {
    val licenses: Iterant[F, Int] = Iterant[F]
      .repeatEvalF(client.issueLicense())
      .mapBatch { license =>
        Batch.fromSeq(Seq.fill(license.digAllowed)(license.id))
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
      val seed = (0 -> Seq.empty[String]).pure[F]

      explorator
        .mapEval { case (x, y, amount) =>
          licenses.foldWhileLeftEvalL(seed) {
            case ((level, foundTreasures), license) =>
              client.dig(license, x, y, level).map { newTreasures =>
                val nextTreasures = newTreasures ++ foundTreasures
                val result = level + 1 -> nextTreasures
                Either.cond(
                  level >= 9 || nextTreasures.size >= amount,
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

    val coins = digger
      .mapEval { treasure =>
        client.cash(treasure)
      }
      .mapBatch(coins => Batch.fromSeq(coins))

    coins.sumL
  }
}
