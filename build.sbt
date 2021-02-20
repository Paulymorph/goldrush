name := "goldrush"

version := "0.1"

scalaVersion := "2.13.4"

libraryDependencies += "io.monix" %% "monix" % "3.3.0"
libraryDependencies += "com.softwaremill.sttp.client3" %% "async-http-client-backend-monix" % "3.1.3"
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", _) => MergeStrategy.discard
  case PathList(properties, _) if properties.endsWith(".properties") => MergeStrategy.first
  case _ => MergeStrategy.deduplicate
}