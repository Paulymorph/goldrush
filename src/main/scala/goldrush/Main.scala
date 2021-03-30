package goldrush

import build.BuildInfo
import cats.effect.ExitCode
import monix.eval.{Task, TaskApp}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import sttp.client3.asynchttpclient.monix.AsyncHttpClientMonixBackend

import scala.concurrent.duration._

object Main extends TaskApp {
  override def run(args: List[String]): Task[ExitCode] =
    for {
      logger <- Slf4jLogger.create[Task]
      _ <- logger.info(BuildInfo.version)
      statistics <- Statistics[Task]
      _ <- Task.deferAction { implicit scheduler =>
        Task.delay {
          scheduler.scheduleOnce(9.minutes + 50.seconds) {
            statistics.getInfo.flatMap { info =>
              logger.info(info.toString)
            }.runAsyncAndForget
          }
        }
      }
      baseUrl <- Config.getBaseUrl[Task]
      backend <- AsyncHttpClientMonixBackend()
      client <- ClientImpl[Task](baseUrl, backend)
      _ <- LimitedClient
        .wrap(1000)(client)
        .map(StatisticsClient.wrap(statistics))
        .use { client => Miner[Task](client).use(_.mine) }
    } yield ExitCode.Success
}
