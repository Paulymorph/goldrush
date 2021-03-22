package goldrush

import java.util.concurrent.atomic.AtomicLong

object Counters {

  val foundCellsCount = new AtomicLong()
  val foundTreasuresCount = new AtomicLong()
  val exploresCount = new AtomicLong()
  val digsCount = new AtomicLong()
  val getLicenceCount = new AtomicLong()
  val cashesCount = new AtomicLong()
  val cashesSum = new AtomicLong()

  def print(): Unit = {
    println(
      s"exploresCount: ${exploresCount.get()}, " +
        s"foundCellsCount: ${foundCellsCount.get()}, " +
        s"getLicenceCount: ${getLicenceCount.get()}, " +
        s"digsCount: ${digsCount.get()}, " +
        s"foundTreasuresCount: ${foundTreasuresCount.get()}, " +
        s"cashesCount: ${cashesCount.get()}" +
        s"cashesSum: ${cashesSum.get()}"
    )
  }

}
