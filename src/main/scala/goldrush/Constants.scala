package goldrush

object Constants {

  val digParallelism = 24
  val exploreParallelism = digParallelism * 4
  val licenceParallelism = digParallelism
  val cashParallelism = 6
  val maxExploreArea = 10
  val clientCapDelay = 5
  val clientFullJitter = 1

  val licenceBufferSize = 10
  val exploreBufferSize = 256
  val digBufferSize = 256

  def print(): Unit = {
    println(
      s"""
        |digParallelism: ${digParallelism}
        |exploreParallelism: ${exploreParallelism}
        |licenceParallelism: ${licenceParallelism}
        |cashParallelism: ${cashParallelism}
        |maxExploreArea: ${maxExploreArea}
        |clientCapDelay: ${clientCapDelay}
        |clientFullJitter: ${clientFullJitter}
        |""".stripMargin
    )
  }

}
