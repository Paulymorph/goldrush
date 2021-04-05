package goldrush

object Config {
  def getBaseUrl: String = {
    val address = env("ADDRESS", "default")
    val port = env("Port", "8000")
    val schema = env("Schema", "http")
    s"$schema://$address:$port"
  }

  private def env(name: String, default: String): String =
    sys.env.getOrElse(name, default)
}
