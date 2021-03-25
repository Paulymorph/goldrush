package goldrush

import java.util.concurrent.PriorityBlockingQueue

import goldrush.Counters.startTime

object Constants {

  val digParallelism = 36
  val exploreParallelism = digParallelism * 2
  val batchExploreParallelism = 16
  val licenceParallelism = 3
  val cashParallelism = 16
  val maxExploreArea = 300
  val clientCapDelay = 2
  val clientFullJitter = 1

  val licenceBufferSize = 50
  val exploreBufferSize = 256
  val digBufferSize = 256
  val goldStoreSize = 256

  def print(): Unit = {
    println(
      s"""
        |digParallelism: $digParallelism,
        |exploreParallelism: $exploreParallelism,
        |licenceParallelism: $licenceParallelism,
        |cashParallelism: $cashParallelism,
        |maxExploreArea: $maxExploreArea,
        |clientCapDelay: $clientCapDelay,
        |clientFullJitter: $clientFullJitter,
        |licenceBufferSize: $licenceBufferSize,
        |exploreBufferSize: $exploreBufferSize,
        |digBufferSize: $digBufferSize,
        |goldStoreSize: $goldStoreSize,
        |""".stripMargin
    )
  }

}
