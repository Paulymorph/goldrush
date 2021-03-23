package goldrush.client_checks

import cats.Traverse
import cats.effect.Timer
import cats.instances.seq._
import cats.syntax.functor._
import goldrush.Types.ThrowableMonadError
import goldrush.{Area, Client, ExploreResponse}

class CheckExplore[F[_]: ThrowableMonadError: Timer](client: Client[F])
    extends MethodChecker[F, Int, ExploreResponse](client) {

  override val dataName: String = "areaSize"
  override val input: Seq[Int] = Seq(1, 5, 10, 25, 50, 100, 300, 500, 1000)

  private def genAreas(size: Int): Seq[Area] = {
    val areasOnSide = Math.min(3500 / size - 1, 30)

    for {
      i <- Range(0, areasOnSide)
      j <- Range(0, areasOnSide)
    } yield Area(i * size, j * size, size, size)
  }

  override def checkMethod(input: Int): F[Seq[Data[ExploreResponse]]] = {
    val areas = genAreas(input)
    val responses = Traverse[Seq].traverse(areas)(ar => timed(_.explore(ar)))

    responses.map { x =>
      val succs = x.collect { case Data(_, _, Right(value)) => value }
      val averageTreasuresPerArea = succs.map(_.amount).sum / succs.size
      println(s"areaSize: $input, averageTreasuresPerArea: $averageTreasuresPerArea")
      x
    }
  }

}
