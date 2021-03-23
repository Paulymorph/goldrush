package goldrush

import java.util.concurrent.atomic.AtomicLong

object Counters {

  val foundCellsCount = new AtomicLong()
  val foundTreasuresCount = new AtomicLong()
  val exploresCount = new AtomicLong()
  val digsCount = new AtomicLong()
  val freeLicenceTriesCount = new AtomicLong()
  val freeLicenceDigsCount = new AtomicLong()
  val paidLicenceTriesCount = new AtomicLong()
  val paidLicenceDigsCount = new AtomicLong()
  val cashesCount = new AtomicLong()
  val cashesSum = new AtomicLong()

  def print(): Unit = {
    println(
      s"foundCells: ${foundCellsCount.get()}, " +
        s"foundTreasures: ${foundTreasuresCount.get()}, " +
        s"explores: ${exploresCount.get()}, " +
        s"digs: ${digsCount.get()}, " +
        s"freeLicenceTries: ${freeLicenceTriesCount.get()}, " +
        s"freeLicenceDigs: ${freeLicenceDigsCount.get()}, " +
        s"paidLicenceTries: ${paidLicenceTriesCount.get()}, " +
        s"paidLicenceDigs: ${paidLicenceDigsCount.get()}, " +
        s"cashes: ${cashesCount.get()}, " +
        s"cashesSum: ${cashesSum.get()}"
    )
  }

}
