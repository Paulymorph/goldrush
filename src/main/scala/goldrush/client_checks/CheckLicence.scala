package goldrush.client_checks

import cats.Traverse
import cats.effect.Timer
import cats.syntax.functor._
import goldrush.Types.ThrowableMonadError
import goldrush.{Client, License}

case class CheckLicence[F[_]: ThrowableMonadError: Timer](client: Client[F])
    extends MethodChecker[F, Int, License](client) {

  override val dataName: String = "threadsCount"
  override val input: Seq[Int] = Seq(16)

  override def checkMethod(input: Int): F[Seq[Data[License]]] = {
    val result: F[Seq[Data[License]]] = Traverse[Seq]
      .traverse(Seq.range(0, 100))(_ => timed(_.issueLicense()))

    result.map { x =>
      println(x.mkString(",\n"))
      x
    }
  }

}
