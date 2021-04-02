package goldrush

import java.util.Collections
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong

import goldrush.Miner.Explorator
import monix.eval.Coeval
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import org.scalatest.{Assertion, BeforeAndAfterAll}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MinerSpec extends AnyFlatSpec with BeforeAndAfterAll with Matchers {
  implicit val s: SchedulerService = Scheduler.computation(name = "test")
  override def afterAll() = {
    s.shutdown()
  }
  private val areaSide = 100
  private val area = Area(0, 0, areaSide, areaSide)
  private val locations: Seq[(Int, Int)] = area.locations

  "Explorator" should "explore all the treasures" in {
    testExplorator(area)((_, _) => true)
  }

  it should "explore only the places with treasures" in {
    testExplorator(area)((x, y) => (x + y) % 2 == 0)
  }

  it should "explore only the places with treasures2" in {
    println(s"size of field: $areaSide")

    val batches: Seq[(String, ExploreMethod)] =
      for {
        maxStep <- Seq(1, 2, 4, 5, 8, 13, 30, 100, 1000)
      } yield s"exploratorBatched$maxStep" -> Miner.exploratorBatched[Coeval](maxStep)

    val methods: Seq[(String, ExploreMethod)] =
      batches ++ Seq("exploratorBinary" -> Miner.exploratorBinary[Coeval])

    methods.map { case (methodName, exploreMethod) =>
      Seq(25).map { frequency =>
        def containsTreasure(x: Int, y: Int): Boolean =
          (x + y * areaSide) % frequency == 0

        def treasures(a: Area): Int = a.locations.map { case (x, y) =>
          if (containsTreasure(x, y)) 1
          else 0
        }.sum

        val callsCounter = new AtomicLong(0)
        val treasureCounter = new AtomicLong(0)
        val zeroAreaExplores = new AtomicLong(0)
        val locationSizes = new AtomicLong(0)

        val explore: Area => Coeval[ExploreResponse] = { a =>
          {
            if (a.sizeX == 0 || a.sizeY == 0) zeroAreaExplores.incrementAndGet()
            callsCounter.incrementAndGet()
            locationSizes.addAndGet(a.locations.size)
            val t = treasures(a)
            treasureCounter.addAndGet(t)
            Coeval.delay(ExploreResponse(a, t))
          }
        }

        val foundPositions =
          exploreMethod(explore)(area, treasures(area)).toListL
            .runSyncUnsafe()

        println(
          s"${String.format("%30s", methodName)}, frequency $frequency, calls: ${callsCounter.get()}, " +
            s"locationSizes: ${locationSizes}"
        )

        val expected = locations
          .map { case (x, y) =>
            (x, y, if (containsTreasure(x, y)) 1 else 0)
          }
          .filter(_._3 == 1)

        foundPositions.sorted shouldBe expected.sorted
      }
    }
  }

  "priority" should "return max" in {
    val prirityQ = new PriorityBlockingQueue[ExploreResponse](
      1000,
      Collections.reverseOrder((o1: ExploreResponse, o2: ExploreResponse) =>
        o1.amount.compareTo(o2.amount)
      )
    )

    prirityQ.put(ExploreResponse(Area(1, 2, 3, 4), 1))
    prirityQ.put(ExploreResponse(Area(1, 2, 3, 4), 2))
    prirityQ.put(ExploreResponse(Area(1, 2, 3, 4), 3))

    prirityQ.take() shouldBe (ExploreResponse(Area(1, 2, 3, 4), 3))
  }

  type ExploreMethod = (Area => Coeval[ExploreResponse]) => (Area, Int) => Explorator

  private def makeExplorator(allArea: Area)(containsTreasure: (Int, Int) => Boolean): Explorator = {
    def treasures(area: Area): Int = area.locations.map { case (x, y) =>
      if (containsTreasure(x, y)) 1 else 0
    }.sum

    Miner.explorator { area =>
      Coeval.delay(ExploreResponse(area, treasures(area)))
    }(allArea, treasures(allArea))
  }

  private def testExplorator(allArea: Area)(containsTreasure: (Int, Int) => Boolean): Assertion = {
    val explorator = makeExplorator(allArea)(containsTreasure)
    val actualResult = explorator.toListL.runSyncUnsafe()

    val expectations = allArea.locations
      .filter { case (x, y) => containsTreasure(x, y) }
      .map { case (x, y) => (x, y, 1) }
    actualResult.sorted shouldBe expectations.sorted
  }

}
