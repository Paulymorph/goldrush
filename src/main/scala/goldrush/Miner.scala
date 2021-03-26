package goldrush

import java.util.{Collections, Comparator}
import java.util.concurrent.{
  ConcurrentHashMap,
  LinkedBlockingQueue,
  PriorityBlockingQueue,
  TimeUnit
}
import java.util.concurrent.atomic.AtomicLong

import cats.effect.concurrent.MVar
import cats.effect.{Concurrent, ContextShift, Resource, Sync, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Parallel}
import goldrush.Miner._
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.{Observable, OverflowStrategy}

import scala.concurrent.duration.{Duration, MILLISECONDS}
case class Miner[F[
    _
]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift](
    client: Client[F]
) {
  def mine: F[Unit] = {
    val digParallelism = 36

//    val licensesCount = new AtomicLong(0)
    val licensesDict = new ConcurrentHashMap[Int, Int](16, 0.75f, 8)
    val moneyQueue = new LinkedBlockingQueue[Seq[Int]](100)

    val licensesR: Resource[F, F[Int]] = {
      for {
        queue <- Resource.liftF(MVar.empty[F, Int])
        _ <- Concurrent[F].background {
          Observable
            .timerRepeated(
              Duration(10, TimeUnit.SECONDS),
              Duration(5, TimeUnit.MILLISECONDS),
              ()
            )
            .mapParallelUnorderedF[F, Option[License]](8) { _ =>
//              println("trying to get license")
              if (licensesDict.size() > 8)
                Applicative[F].pure(None)
              else
                client
                  .issueLicense(
//                    Some(moneyQueue.poll(1, TimeUnit.MILLISECONDS))
//                      .map(_.take(1))
//                      .getOrElse(Seq.empty): _*
                  )
                  .map(Option(_))
            }
            .flatMap(Observable.fromIterable(_))
            .flatMapIterable { license =>
              licensesDict.put(license.id, license.digAllowed)
//              licensesCount.addAndGet(license.digAllowed)
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
        .flatMap { licenseF =>
          explorator(client.explore)(Area(0, 0, 3500, 3500), 490_000)
            .mapParallelOrderedF(digParallelism) { case (x, y, amount) =>
              def decreaseLicense(licenceId: Int) = {
                val v = licensesDict.compute(licenceId, { (k, v) => v - 1 })
                if (v == 0) licensesDict.remove(licenceId)
              }

              def dig(
                  level: Int,
                  foundTreasures: Seq[String]
              ): F[Seq[String]] = {
                for {
                  license <- licenseF
                  newTreasures <- client.dig(license, x, y, level)
                  _ = decreaseLicense(license)
                  nextTreasures = foundTreasures ++ newTreasures
                  goDeeper = level < 10 && nextTreasures.size < amount
                  result <-
                    if (goDeeper) dig(level + 1, nextTreasures)
                    else Applicative[F].pure(nextTreasures)
                } yield result
              }

              dig(1, Seq.empty)
            }
            .flatMap(Observable.fromIterable)
        }
    }

    digger
      .mapParallelUnorderedF(digParallelism) { treasure =>
        client.cash(treasure)
      }
      .foreachL(moneyQueue.offer(_))
      .to[F]
  }
}

object Miner {
  type X = Int
  type Y = Int
  type Amount = Int
  type Explorator = Observable[(X, Y, Amount)]

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

  val prirityQ = new PriorityBlockingQueue[ExploreResponse](
    10000,
    Collections.reverseOrder((o1: ExploreResponse, o2: ExploreResponse) =>
      o1.amount.compareTo(o2.amount)
    )
  )

  def exploratorBatched[F[_]: TaskLike: Applicative](maxStep: Int = 5)(
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    import area._
    val xs = Observable.range(posX, posX + sizeX, maxStep).map(_.toInt)
    val ys = Observable.range(posY, posY + sizeY, maxStep).map(_.toInt)
    val coords = xs.flatMap(x => ys.flatMap(y => Observable.pure(x, y)))

    coords
      .mapParallelUnorderedF(8) { case (x, y) =>
        explore(
          Area(
            x,
            y,
            Math.min(maxStep, posX + sizeX - x),
            Math.min(maxStep, posY + sizeY - y)
          )
        )
      }
      .filter(_.amount > 0)
      .map { x =>
        prirityQ.add(x)
        x
      }
      .asyncBoundary(OverflowStrategy.BackPressure(1000))
      .map(_ => prirityQ.take())
      .flatMap { response =>
//        println(s"prirityQ size: ${prirityQ.size()}")
//        Observable
//          .from(Applic/ative[F].pure(Thread.sleep(100)))
//          .flatMap(_ =>
        exploratorBinary(explore)(response.area, response.amount)
          .asyncBoundary(OverflowStrategy.BackPressure(100))
      }
//      .completed
  }

  def explorator[F[_]: TaskLike: Applicative](
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    exploratorBatched(15)(explore)(area, amount)
  }

}
