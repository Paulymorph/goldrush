package goldrush

object Constants {

  val digParallelism = 16
  val exploreParallelism = digParallelism * 2
  val licenceParallelism = digParallelism / 3
  val cashParallelism = 4
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
