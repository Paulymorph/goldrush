package goldrush

import java.util.concurrent.atomic.AtomicLong

import cats.Id
import goldrush.Miner.Explorator
import monix.eval.Coeval
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MinerSpec extends AnyFlatSpec with BeforeAndAfterAll with Matchers {
  implicit val s: SchedulerService = Scheduler.computation(name = "test")
  override def afterAll() = {
    s.shutdown()
  }
  val sss = 100
  val area = Area(0, 0, sss, sss)
  private val locations: Seq[(Int, Int)] = area.locations

  "Explorator" should "explore all the treasures" in {
    val explore: Area => Coeval[ExploreResponse] =
      a => Coeval.pure(ExploreResponse(a, a.locations.size))

    val foundPositions = Miner
      .explorator(explore)(area, locations.size)
      .toListL
      .runSyncUnsafe()

    foundPositions.sorted shouldBe locations.map { case (x, y) =>
      (x, y, 1)
    }.sorted
  }

  it should "explore only the places with treasures" in {
    def containsTreasure(x: Int, y: Int): Int = (x + y) % 2
    def treasures(a: Area): Int = a.locations.map { case (x, y) =>
      containsTreasure(x, y)
    }.sum

    val explore: Area => Coeval[ExploreResponse] =
      a => Coeval.delay(ExploreResponse(a, treasures(a)))

    val foundPositions = Miner
      .explorator(explore)(area, treasures(area))
      .toListL
      .runSyncUnsafe()

    val expected = locations
      .map { case (x, y) =>
        (x, y, containsTreasure(x, y))
      }
      .filter(_._3 != 0)
    foundPositions.sorted shouldBe expected.sorted
  }

  it should "explore only the places with treasures2" in {
    println(s"size of field: $sss")

    val methods: Seq[(String, (Area => Coeval[ExploreResponse]) => (Area, Int) => Explorator)] = Seq(
      "exploratorBatched1" -> Miner.exploratorBatched[Coeval](1),
      "exploratorBatched2" -> Miner.exploratorBatched[Coeval](2),
      "exploratorBatched5" -> Miner.exploratorBatched[Coeval](5),
      "exploratorBatched13" -> Miner.exploratorBatched[Coeval](13),
      "exploratorBatched30" -> Miner.exploratorBatched[Coeval](30),
      "exploratorBatched100" -> Miner.exploratorBatched[Coeval](100),
      "exploratorBatched1000" -> Miner.exploratorBatched[Coeval](1000),
      "exploratorBinary" -> Miner.exploratorBinary[Coeval],
      "exploratorBy3" -> Miner.exploratorBy3[Coeval]
    )

    methods.map { case (methodName, exploreMethod) =>
      Seq(25).map { frequency =>
        def containsTreasure(x: Int, y: Int): Boolean =
          (x + y * sss) % frequency == 0

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

        val foundPositions = exploreMethod(explore)(area, treasures(area))
          .toListL
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

}
