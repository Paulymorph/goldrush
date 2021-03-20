package goldrush.client_checks

import cats.Traverse
import cats.effect.Timer
import cats.instances.seq._
import goldrush.Types.ThrowableMonadError
import goldrush.{Area, Client, ExploreResponse}

class CheckExplore[F[_]: ThrowableMonadError: Timer](client: Client[F])
    extends MethodChecker[F, Int, ExploreResponse](client) {

  override val dataName: String = "areaSize"
  override val input: Seq[Int] = Seq.range(1, 40)

  private def genAreas(size: Int): Seq[Area] = for {
    i <- Range(0, 10)
    j <- Range(0, 10)
  } yield Area(i * size, j * size, size, size)

  override def checkMethod(input: Int): F[Seq[Data[ExploreResponse]]] = {
    val areas = genAreas(input)
    Traverse[Seq].traverse(areas)(ar => timed(_.explore(ar)))
  }

}
