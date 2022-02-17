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
      "ch.qos.logback" % "logback-classic" % "1.2.10",
      "io.circe" %% "circe-config" % "0.8.0",
      "io.circe" %% "circe-core" % V.circe,
      "io.circe" %% "circe-generic" % V.circe,
      "io.circe" %% "circe-parser" % V.circe,
      "org.http4s" %% "http4s-circe" % V.http4s,
      "org.http4s" %% "http4s-jdk-http-client" % "0.5.0",
    ),

    assembly / assemblyJarName := s"${name.value}-${version.value}.sh.bat",

    assembly / assemblyOption := (assembly / assemblyOption).value
      .withPrependShellScript(Some(AssemblyPlugin.defaultUniversalScript(shebang = false))),

    assembly / assemblyMergeStrategy := {
      case PathList(paths@_*) if paths.last == "module-info.class" => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },
  )
