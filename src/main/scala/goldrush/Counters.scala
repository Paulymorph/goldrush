package goldrush

import java.util.concurrent.atomic.AtomicLong

object Counters {

  val foundCellsCount = new AtomicLong()
  val foundTreasuresCount = new AtomicLong()
  val exploresCount = new AtomicLong()
  val digsCount = new AtomicLong()
  val getLicenceCount = new AtomicLong()
  val cashesCount = new AtomicLong()

  def print(): Unit = {
    println(
      s"foundCellsCount: ${foundCellsCount.get()}, " +
        s"foundTreasuresCount: ${foundTreasuresCount.get()}, " +
        s"exploresCount: ${exploresCount.get()}, " +
        s"digsCount: ${digsCount.get()}, " +
        s"getLicenceCount: ${getLicenceCount.get()}, " +
        s"cashesCount: ${cashesCount.get()}"
    )
  }

}
