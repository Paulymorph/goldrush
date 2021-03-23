package goldrush

import java.util.concurrent.atomic.AtomicLong

object Counters {

  val foundCellsCount = new AtomicLong()
  val foundTreasuresCount = new AtomicLong()
  val exploresCount = new AtomicLong()
  val digsCount = new AtomicLong()
  val licenceCount = new AtomicLong()
  val licenceTriesCount = new AtomicLong()
  val cashesCount = new AtomicLong()
  val cashesSum = new AtomicLong()

  def print(): Unit = {
    println(
      s"explores: ${exploresCount.get()}, " +
        s"foundCells: ${foundCellsCount.get()}, " +
        s"licences: ${licenceCount.get()}, " +
        s"licenceTries: ${licenceTriesCount.get()}, " +
        s"digs: ${digsCount.get()}, " +
        s"foundTreasures: ${foundTreasuresCount.get()}, " +
        s"cashes: ${cashesCount.get()}" +
        s"cashesSum: ${cashesSum.get()}"
    )
  }

}
