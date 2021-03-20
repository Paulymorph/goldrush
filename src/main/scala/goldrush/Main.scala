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
      fakeClient = new FakeClient[Task]
      timingExplorer = new CheckExploreTimings[Task](client)
      _ <- timingExplorer.exploreTimings
    } yield ExitCode.Success
}
