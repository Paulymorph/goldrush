package goldrush

import cats.effect.ExitCode
import goldrush.client_checks.FakeClient
import monix.eval.{Task, TaskApp}
import sttp.client3.asynchttpclient.monix.AsyncHttpClientMonixBackend

object Main extends TaskApp {
  override def run(args: List[String]): Task[ExitCode] = {
    println(s"Started. ${DockerTag.dockerTag}")
    Constants.print()
    for {
      baseUrl <- Config.getBaseUrl[Task]
      backend <- AsyncHttpClientMonixBackend()
      client = new FakeClient[Task]
      _ <- Miner[Task](client).use(_.mine)
      _ <- backend.close()
    } yield ExitCode.Success
  }
}
