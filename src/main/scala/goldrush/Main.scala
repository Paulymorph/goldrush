package goldrush

import cats.effect.ExitCode
import goldrush.client_checks.{CheckExplore, CheckLicence, FakeClient}
import monix.eval.{Task, TaskApp}
import sttp.client3.asynchttpclient.monix.AsyncHttpClientMonixBackend

object Main extends TaskApp {
  override def run(args: List[String]): Task[ExitCode] =
    for {
      baseUrl <- Config.getBaseUrl[Task]
      backend <- AsyncHttpClientMonixBackend()
//      client <- ClientImpl[Task](baseUrl, backend)
      client = new FakeClient[Task]()
//      checker = new CheckLicence[Task](client)
//      checker = new CheckExplore[Task](client)
//      _ <- checker.run
      miner = Miner(client)
      _ <- miner.mine
      _ <- backend.close()
    } yield ExitCode.Success
}
