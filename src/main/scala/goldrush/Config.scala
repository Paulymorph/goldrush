package goldrush

import cats.effect.Sync
import cats.syntax.all._

object Config {
  def getBaseUrl[F[_]: Sync]: F[String] =
    (
      env("ADDRESS", "default"),
      env("Port", "8000"),
      env("Schema", "http")
    ).mapN {(address, port, schema) =>
      s"$schema://$address:$port"
    }

  private def env[F[_]: Sync](name: String, default: String): F[String] = Sync[F].delay(sys.env.getOrElse(name, default))
}
