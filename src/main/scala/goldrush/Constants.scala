package goldrush

object Constants {

  val digParallelism = 64
  val exploreParallelism = digParallelism * 2
  val licenceParallelism = digParallelism / 8
  val cashParallelism = 8
  val maxExploreArea = 100
  val clientCapDelay = 3
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
