name := "goldrush"

version := "0.1"

scalaVersion := "2.13.4"

val monix = "3.3.0"
libraryDependencies ++=
  Seq(
    "io.monix" %% "monix",
    "io.monix" %% "monix-tail"
  ).map(_ % monix)

val sttp = "3.1.3"
libraryDependencies ++=
  Seq(
    "com.softwaremill.sttp.client3" %% "async-http-client-backend-monix",
    "com.softwaremill.sttp.client3" %% "circe"
  ).map(_ % sttp)

val catsRetry = "2.1.0"
libraryDependencies += "com.github.cb372" %% "cats-retry" % catsRetry

val circe = "0.12.3"
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circe)

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", _) => MergeStrategy.discard
  case PathList(properties, _) if properties.endsWith(".properties") => MergeStrategy.first
  case _ => MergeStrategy.deduplicate
}