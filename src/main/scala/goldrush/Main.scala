package goldrush

import cats.effect.ExitCode
import monix.eval.{Task, TaskApp}
import sttp.client3.asynchttpclient.monix.AsyncHttpClientMonixBackend

object Main extends TaskApp {
  override def run(args: List[String]): Task[ExitCode] =
    for {
      baseUrl <- Config.getBaseUrl[Task]
      backend <- AsyncHttpClientMonixBackend()
      client <- ClientImpl[Task](baseUrl, backend)
      _ <- Miner[Task](client).use(_.mine)
    } yield ExitCode.Success
}
