package goldrush

import cats.effect.ExitCode
import monix.eval.{Task, TaskApp}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.client3.asynchttpclient.monix.AsyncHttpClientMonixBackend

object Main extends TaskApp {
  override def run(args: List[String]): Task[ExitCode] =
    Statistics[Task].bracket { statistics =>
      for {
        baseUrl <- Config.getBaseUrl[Task]
        backend <- AsyncHttpClientMonixBackend()
        client <- ClientImpl[Task](baseUrl, backend).map(StatisticsClient.wrap(statistics))
        _ <- Miner[Task](client).use(_.mine)
      } yield ExitCode.Success
    } { statistics =>
      for {
        logger <- Slf4jLogger.create[Task]
        info <- statistics.getInfo
        _ <- logger.info(info.toString)
      } yield ()
    }
}
