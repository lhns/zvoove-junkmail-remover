ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

val V = new {
  val circe = "0.14.1"
  val http4s = "0.23.9"
}

lazy val root = (project in file("."))
  .settings(
    name := "zvoove-junkmail-remover",

    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % V.circe,
      "io.circe" %% "circe-generic" % V.circe,
      "io.circe" %% "circe-parser" % V.circe,
      "org.http4s" %% "http4s-circe" % V.http4s,
      "org.http4s" %% "http4s-jdk-http-client" % "0.5.0",
    )
  )
