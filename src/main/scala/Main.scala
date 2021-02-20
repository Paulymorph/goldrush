import cats.effect.ExitCode
import monix.eval.{Task, TaskApp}

object Main extends TaskApp {
  override def run(args: List[String]): Task[ExitCode] =
    Task.delay(println("hello")) *>
    Task.pure(ExitCode.Success)
}
