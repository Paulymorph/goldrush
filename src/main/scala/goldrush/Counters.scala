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
      s"explores: ${exploresCount.get()}, " +
        s"foundCells: ${foundCellsCount.get()}, " +
        s"licences: ${getLicenceCount.get()}, " +
        s"digs: ${digsCount.get()}, " +
        s"foundTreasures: ${foundTreasuresCount.get()}, " +
        s"cashes: ${cashesCount.get()}" +
    )
  }

}
