import sbt.Def.settings

name := "goldrush"

scalaVersion := "2.13.4"

val monix = "3.3.0"
libraryDependencies ++=
  Seq(
    "io.monix" %% "monix",
    "io.monix" %% "monix-tail",
    "io.monix" %% "monix-reactive"
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

val log4cats = "1.2.0"
libraryDependencies += "org.typelevel" %% "log4cats-slf4j" % log4cats

val logging = "1.7.30"
libraryDependencies += "org.slf4j" % "slf4j-simple" % logging

val scalatest = "3.2.5"
libraryDependencies ++=
  Seq(
    "org.scalactic" %% "scalactic" % scalatest % Test,
    "org.scalatest" %% "scalatest" % scalatest % Test
  )

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", _) => MergeStrategy.discard
  case PathList(properties, _) if properties.endsWith(".properties") =>
    MergeStrategy.first
  case _ => MergeStrategy.deduplicate
}

enablePlugins(BuildInfoPlugin, GitVersioning)

settings(
  buildInfoKeys := Seq[BuildInfoKey](version),
  buildInfoPackage := "build",
  buildInfoObject := "BuildInfo"
)
