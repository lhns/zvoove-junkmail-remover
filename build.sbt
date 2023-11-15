ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.11"

val V = new {  
  val betterMonadicFor = "0.3.1"
  val circe = "0.14.6"
  val circeConfig = "0.10.1"
  val http4s = "0.23.23"  
  val http4sJdkHttpClient = "0.9.1"
  val logbackClassic = "1.4.11"
}

lazy val root = (project in file("."))
  .settings(
    name := "zvoove-junkmail-remover",

    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % V.logbackClassic,
      "io.circe" %% "circe-config" % V.circeConfig,
      "io.circe" %% "circe-generic" % V.circe,
      "io.circe" %% "circe-parser" % V.circe,
      "org.http4s" %% "http4s-circe" % V.http4s,
      "org.http4s" %% "http4s-jdk-http-client" % V.http4sJdkHttpClient,
    ),

    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % V.betterMonadicFor),

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
