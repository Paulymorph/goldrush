package goldrush

import cats.effect.concurrent.{MVar, Ref}
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.syntax.traverse._
import cats.{Applicative, Parallel}

case class Miner[F[_]: Sync: Parallel: Applicative: Concurrent: ContextShift](
    client: Client[F]
) {
  def mine: F[Int] = {
    val licensesR: Resource[F, F[Int]] = {
      for {
        queue <- Resource.liftF(MVar.empty[F, Int])
        _ <- Concurrent[F].background {
          fs2.Stream
            .repeatEval(client.issueLicense())
            .evalMap { license =>
              val licensesUses =
                Seq.fill(license.digAllowed - license.digUsed)(license.id)
              licensesUses.traverse(queue.put)
            }
            .compile
            .drain
        }
      } yield queue.take
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
      fs2.Stream.resource(licensesR).flatMap { nextLicense =>
        explorator
          .evalMap { case (area, amount) =>
            for {
              foundTreasures <- Ref[F].of(Seq.empty[String])
              _ <- area.locations.parTraverse { case (x, y) =>
                def dig(level: Int): F[Unit] = {
                  for {
                    license <- nextLicense
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
