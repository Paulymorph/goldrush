package goldrush

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
  val area = Area(0, 0, 50, 50)
  private val locations: Seq[(Int, Int)] = area.locations

  "Explorator" should "explore all the treasures" in {
    val explore: Area => Coeval[ExploreResponse] =
      a => Coeval.pure(ExploreResponse(a, 1))

    val foundPositions = Miner
      .explorator(explore)(area, locations.size)
      .toListL
      .runSyncUnsafe()

    foundPositions should contain theSameElementsAs locations.map {
      case (x, y) =>
        (x, y, 1)
    }
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
    foundPositions should contain theSameElementsAs expected
  }
}
