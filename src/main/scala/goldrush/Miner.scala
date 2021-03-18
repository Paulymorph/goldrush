package goldrush

import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.{Applicative, Parallel}
import monix.catnap.ConcurrentQueue

case class Miner[F[_]: Sync: Parallel: Applicative: Concurrent: ContextShift](
    client: Client[F]
) {
  def mine: F[Int] = {
    val licensesR: Resource[F, fs2.Stream[F, Int]] = {
      for {
        queue <- Resource.liftF(ConcurrentQueue.bounded[F, Int](3))
        _ <- Concurrent[F].background {
          fs2.Stream
            .repeatEval(client.issueLicense())
            .evalMap { license =>
              val licensesUses =
                Seq.fill(license.digAllowed - license.digUsed)(license.id)
              queue.offerMany(licensesUses)
            }
            .compile
            .drain
        }
      } yield fs2.Stream.repeatEval(queue.poll)
    }

    val explorator = {
      val sideSize = 3500
      val side = fs2.Stream(0 until sideSize: _*)
      val coords = side.flatMap(x => side.flatMap(y => fs2.Stream(x -> y)))

      coords
        .evalMap { case (x, y) =>
          client.explore(Area(x, y, 1, 1))
        }
        .filter(_.amount > 0)
        .map { result =>
          (result.area, result.amount)
        }
    }

    val digger = {
      fs2.Stream.resource(licensesR).flatMap { licenses =>
        explorator
          .evalMap { case (area, amount) =>
            for {
              foundTreasures <- Ref[F].of(Seq.empty[String])
              _ <- area.locations.parTraverse { case (x, y) =>
                def dig(level: Int): F[Unit] = {
                  for {
                    license <- licenses.head.compile.lastOrError
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
          .flatMap(fs2.Stream.emits)
      }
    }

    val coins = digger
      .evalMap { treasure =>
        client.cash(treasure)
      }
      .flatMap(fs2.Stream.emits)

    coins.compile.foldMonoid
  }
}
