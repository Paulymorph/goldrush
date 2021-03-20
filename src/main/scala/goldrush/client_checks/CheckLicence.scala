package goldrush.client_checks

import cats.effect.Timer
import goldrush.Types.ThrowableMonadError
import goldrush.{Client, License}
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.Observable

case class CheckLicence[F[_]: ThrowableMonadError: Timer: TaskLift: TaskLike](
    client: Client[F]
) extends MethodChecker[F, Int, License](client) {

  override val dataName: String = "threadsCount"
  override val input: Seq[Int] = Seq(1, 2, 3, 4, 5, 8, 16, 32)

  override def checkMethod(input: Int): F[Seq[Data[License]]] = {
    val request = Observable
      .range(0, 1000)
      .mapParallelUnorderedF(input)(_ => timed(_.issueLicense()))

    TaskLift[F]
      .apply(request.toListL.map(_.toSeq))
  }

}
