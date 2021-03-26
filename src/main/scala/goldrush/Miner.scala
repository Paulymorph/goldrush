package goldrush

import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue, PriorityBlockingQueue}

import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.functor._
import cats.{Applicative, Parallel}
import goldrush.Constants._
import goldrush.Licenser.{Issuer, LicenseId}
import goldrush.Miner.{exploratorBatched, exploratorQueue2}
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.Observable

import scala.jdk.CollectionConverters._

class Miner[F[_]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift](
    goldStore: GoldStore[F],
    licenser: Observable[LicenseId],
    client: Client[F]
) {
  def mine: F[Unit] = {
    (for {
      _ <- exploratorBatched(maxExploreArea)(client.explore)(
        Area(0, 0, 3500, 3500),
        490_000
      )
      _ <- exploratorQueue2(client.explore)
    } yield {
      val c = Counters.foundCellsCount.incrementAndGet()
      if (c % 3000 == 0) {
        Counters.print()
        Miner.printTreasureForArea()
      }
    }).completedF
  }
}

object Miner {
  type X = Int
  type Y = Int
  type Amount = Int
  type Explorator = Observable[(X, Y, Amount)]

  val treasuresForBatchArea = new ConcurrentHashMap[Int, Int](128, 0.75f, 8)

  def printTreasureForArea(): Unit = {
    println(
      Miner.treasuresForBatchArea
        .entrySet()
        .asScala
        .toSeq
        .sortBy(_.getKey)
        .map { entry => s"(${entry.getKey}, ${entry.getValue})" }
        .mkString(", ")
    )
  }

  val priorityQueue = new PriorityBlockingQueue[(Area, Amount, Int)](
    10000,
    (o1: (Area, Amount, Int), o2: (Area, Amount, Int)) => o1._3.compareTo(o2._3)
  )

  val binaryQueue = new LinkedBlockingQueue[(Area, Amount)]

  def apply[F[_]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift](
      client: Client[F]
  ): Resource[F, Miner[F]] = {
    for {
      goldStore <- Resource.liftF(GoldStoreImpl[F](goldStoreSize))
      licenser = Licenser.applyObserve(licenceParallelism, Issuer.free(client))
      miner = new Miner[F](goldStore, licenser, client)
    } yield miner
  }

  def putToPriorityQ(response: ExploreResponse): Boolean = {
    val areaArea = response.area.sizeX * response.area.sizeY
    println(s"putting to pQ: ${(response.area, response.amount, response.amount * 25 - areaArea)}")
    priorityQueue.put((response.area, response.amount, response.amount * 25 - areaArea))
    false
  }

  def getTwoParts(area: Area): (Area, Area) = {
    val Area(x, y, sizeX, sizeY) = area

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
  }

//  def exploratorQueue[F[_]: TaskLike: Applicative](
//      explore: Area => F[ExploreResponse]
//  ): Observable[Unit] = {
//    Observable
//      .repeatEval(priorityQueue.take())
//      .mapParallelOrderedF[F, Unit](exploreParallelism) { case (area, amount, priority) =>
//        val Area(x, y, sizeX, sizeY) = area
//        if (amount == 0 || sizeX * sizeY < 1) Applicative[F].pure(())
//        else if (sizeX * sizeY <= 25) {
//          Applicative[F].pure(binaryQueue.put((area, amount)))
//        } else {
//          val (left, right) = getTwoParts(area)
//
//          explore(left)
//            .map { left =>
//              Seq(left, ExploreResponse(right, amount - left.amount))
//                .filter(_.amount > 0)
//                .map(putToPriorityQ)
//            }
//
//        }
//      }
//  }

  def exploratorQueue2[F[_]: TaskLike: TaskLift: Applicative](
      explore: Area => F[ExploreResponse]
  ): Explorator = {
    Observable
      .repeatEval(priorityQueue.take())
      .flatMap { case (area, amount, priority) =>
        val Area(x, y, sizeX, sizeY) = area
        if (amount == 0 || sizeX * sizeY < 1) Observable.empty[(X, Y, Amount)]
        else if (sizeX * sizeY <= 25) doTheBinaryJob(explore)(area, amount)
        else {
          val (left, right) = getTwoParts(area)

//          if (priorityQueue.size() % 10 == 0)
//            println(s"priorityQ: ${priorityQueue.}")

          Observable
            .from(
              explore(left)
                .map { left =>
                  Seq(left, ExploreResponse(right, amount - left.amount))
                    .filter(_.amount > 0)
                    .map(putToPriorityQ)
                }
            )
            .flatMap(_ => Observable.empty[(X, Y, Amount)])
        }
      }
  }

  def doTheBinaryJob[F[_]: TaskLike](
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Amount): Observable[(X, Y, Amount)] = {
    val Area(x, y, sizeX, sizeY) = area
    if (amount == 0 || sizeX * sizeY < 1) Observable.empty
    else if (sizeX * sizeY == 1) Observable((x, y, amount))
    else {
      val (left, right) = getTwoParts(area)

      val exploreadAreas = Observable
        .from(explore(left))
        .flatMapIterable { explored =>
          Seq(explored, ExploreResponse(right, amount - explored.amount))
        }

      exploreadAreas
        .filter(_.amount > 0)
        .flatMap { explored =>
          doTheBinaryJob(explore)(explored.area, explored.amount)
        }
    }
  }

  def exploratorBinary[F[_]: TaskLike](
      explore: Area => F[ExploreResponse]
  ): Explorator = {
    Observable
      .repeatEval(binaryQueue.take())
      .flatMap(x => doTheBinaryJob(explore)(x._1, x._2))
  }

  def exploratorBatched[F[_]: TaskLike: Applicative](maxStep: Int)(
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Observable[Boolean] = {
    import area._
    val xs = Observable.range(posX, posX + sizeX, maxStep).map(_.toInt)
    val ys = Observable.range(posY, posY + sizeY, maxStep).map(_.toInt)
    val coords = xs.flatMap(x => ys.flatMap(y => Observable.pure(x, y)))

    Observable
      .fromIteratorF(
        coords
          .mapParallelUnorderedF(exploreParallelism) { case (x, y) =>
            explore(
              Area(
                x,
                y,
                Math.min(maxStep, posX + sizeX - x),
                Math.min(maxStep, posY + sizeY - y)
              )
            )
          }
          .toListL
          .map(_.sortBy(x => -x.amount).iterator)
      )
      .filter(_.amount > 0)
      .map(putToPriorityQ)

  }

//  def explorator[F[_]: TaskLike: Applicative](
//      explore: Area => F[ExploreResponse]
//  )(area: Area, amount: Int): Explorator = {
//    exploratorBatched(maxExploreArea)(explore)(area, amount)
//  }

}
