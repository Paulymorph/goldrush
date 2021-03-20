package goldrush

import cats.effect.ExitCode
import goldrush.client_checks.{CheckExplore, CheckLicence, FakeClient}
import monix.eval.{Task, TaskApp}

object FakeMain extends TaskApp {
  override def run(args: List[String]): Task[ExitCode] = {
    val client = new FakeClient[Task]()
//    val checker = new CheckExploreTimings[Task](client)
    val checker = new CheckLicence[Task](client)
    checker.run.map(_ => ExitCode.Success)
  }
}
