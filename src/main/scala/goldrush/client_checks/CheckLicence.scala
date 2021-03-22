package goldrush.client_checks

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

import cats.Applicative
import cats.effect.Timer
import cats.syntax.flatMap._
import goldrush.Types.ThrowableMonadError
import goldrush.{Client, License}
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.Observable

case class CheckLicence[F[_]: ThrowableMonadError: Timer: TaskLike: TaskLift](
    client: Client[F]
) extends MethodChecker[F, Int, License](client) {

  override val dataName: String = "threadsCount"
  override val input: Seq[Int] = Seq(4, 8)

  override def checkMethod(input: Int): F[Seq[Data[License]]] = {
//    val result: F[Seq[Data[License]]] = Traverse[Seq]
//      .traverse(Seq.range(0, 100))(_ => timed(_.issueLicense()))

    val sumS = new AtomicLong()
    val countS = new AtomicLong()
    val sumF = new AtomicLong()
    val countF = new AtomicLong()

    val licenses = new ConcurrentLinkedQueue[Long]()

    TaskLift[F]
      .apply {
        Observable
          .repeat(())
          .mapParallelUnorderedF(256)(_ => timed(_.issueLicense()))
          .foreachL { x =>
            x.result match {
              case Right(_) =>
                sumS.addAndGet(x.duration)
                countS.incrementAndGet()
              case Left(_) =>
                sumF.addAndGet(x.duration)
                countF.incrementAndGet()
            }
            val sums = Math.max(sumS.get(), 1)
            val counts = Math.max(countS.get(), 1)
            val sumf = Math.max(sumF.get(), 1)
            val countf = Math.max(countF.get(), 1)
            println(
              s"sumS: $sums, countS: $counts, avgS: ${(sums.toFloat / counts)
                .formatted("%.1f")}ms, rpsS: ${counts * 1000 / sums}, " +
                s"sumF: $sumf, countF: $countf, avgF: ${(sumf.toFloat / countf)
                  .formatted("%.1f")}ms, rpsF: ${countf * 1000 / sumf}, " +
                s"$x"
            )
          }
      }
      .flatMap(_ => Applicative[F].pure(Seq.empty))
  }

}
