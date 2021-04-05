package simplerush

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import goldrush.{Area, Client, ExploreResponse}

import scala.concurrent.{ExecutionContext, Future}

class SimpleMiner(
    client: Client[Future],
    digsContext: ExecutionContext,
    exploreContext: ExecutionContext
) {

  def mine(): Future[Unit] = {

//    val licencesQ = new LinkedBlockingQueue[Int]()
//    val licensesMap = new ConcurrentHashMap[Int, Int](16, 0.75f, 10)
    val cellsQ = new LinkedBlockingQueue[ExploreResponse](100)

//    val treasuresCount =
    val areaSize = 3500

    val coords: Iterator[(Int, Int)] = for {
      i <- Iterator.range(0, areaSize)
      j <- Iterator.range(0, areaSize)
    } yield (i, j)

    val runtime = Runtime.getRuntime

    def runExploreOuter(): Future[Iterator[Unit]] = {
      implicit val ec = exploreContext
      def runExplore(): Future[Iterator[Unit]] = {
        Future
          .traverse(coords.take(6)) { case (i, j) =>
//            println(
//              Seq(
//                "totalMemory" -> runtime.totalMemory(),
//                "maxMemory" -> runtime.maxMemory(),
//                "freeMemory" -> runtime.freeMemory()
//              ).map { case (k, v) => k + ":" + v }.mkString(",")
//            )
            println(s"cells.size: ${cellsQ.size()}")
            client
              .explore(Area(i, j, 1, 1))
              .map(exploreRes => if (exploreRes.amount > 0) cellsQ.put(exploreRes) else ())
          }
          .andThen(_ => runExplore())
      }
      runExplore()
    }

    def runDig(): Future[Seq[Unit]] = {
      implicit val ec = digsContext
      Future.traverse(Seq.range(0, 4)) { _ =>
        val digsLeft = new AtomicLong(0)
        val licenceRef = new AtomicInteger()

        def startNewDig(): Future[Unit] = {
          var area = cellsQ.poll()
          while (area == null) {
            Thread.sleep(10)
            area = cellsQ.poll()
          }
          digOne(area, 0, 1)
            .andThen(_ => startNewDig())
        }

        def digOne(exploreResp: ExploreResponse, foundTreasure: Int, depth: Int): Future[Unit] = {
          for {
            license <- {
              if (digsLeft.get() > 0) {
                Future.successful {
                  digsLeft.decrementAndGet()
                  licenceRef.get()
                }
              } else
                client.issueLicense().map { l =>
                  digsLeft.set(l.digAllowed - l.digUsed - 1)
                  licenceRef.set(l.id)
                  l.id
                }
            }
            digRes <- client.dig(license, exploreResp.area.posX, exploreResp.area.posY, depth)
            _ <-
              if (digRes.nonEmpty)
                Future.traverse(digRes)(client.cash)
              else Future.successful(Seq.empty)
            newFoundTr = digRes.size + foundTreasure
            _ <-
              if (depth >= 10 || newFoundTr >= exploreResp.amount) Future.unit
              else digOne(exploreResp, newFoundTr, depth + 1)
          } yield ()
        }

        startNewDig()
      }
    }

    val exploreFut = runExploreOuter()
    val digFut = runDig()

    {
      import scala.concurrent.ExecutionContext.Implicits.global
      for {
        _ <- exploreFut
        _ <- digFut
      } yield ()
    }
  }

}
