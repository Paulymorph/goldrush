package goldrush

import cats.effect.{ExitCode, Timer}
import monix.eval.{Task, TaskApp}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.client3.asynchttpclient.monix.AsyncHttpClientMonixBackend
import build.BuildInfo
import scala.concurrent.duration._

object Main extends TaskApp {
  override def run(args: List[String]): Task[ExitCode] =
    for {
      logger <- Slf4jLogger.create[Task]
      _ <- logger.info(BuildInfo.version)
      baseUrl <- Config.getBaseUrl[Task]
      backend <- AsyncHttpClientMonixBackend()
      client <- ClientImpl[Task](baseUrl, backend)
      _ <- Miner[Task](client).use(_.mine)
    } yield ExitCode.Success
}
