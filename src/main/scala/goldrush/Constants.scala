package goldrush

object Constants {

  val digParallelism = 24
  val exploreParallelism = digParallelism * 3 / 2
  val licenceParallelism = digParallelism * 3 / 2
  val cashParallelism = 6
  val maxExploreArea = 10
  val clientCapDelay = 5
  val clientFullJitter = 1

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
