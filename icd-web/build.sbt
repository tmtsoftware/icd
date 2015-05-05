import sbt.Project.projectToRef

lazy val clients = Seq(icdWebClient)
lazy val scalaV = "2.11.6"

lazy val icdWebServer = (project in file("icd-web-server")).settings(
  scalaVersion := scalaV,
  scalaJSProjects := clients,
  pipelineStages := Seq(scalaJSProd, gzip),
  includeFilter in (Assets, LessKeys.less) := "*.less",
  libraryDependencies ++= Seq(
    filters,
    "org.tmt"     %% "icd-db" % "0.1-SNAPSHOT",
    "com.vmunier" %% "play-scalajs-scripts" % "0.2.0",
    "org.webjars" % "jquery" % "2.1.3",
    "org.webjars" %% "webjars-play" % "2.3.0-3",
    "org.webjars" % "bootstrap" % "3.3.4",
    "org.webjars.bower" % "bootstrap-table" % "1.7.0"
  )
).enablePlugins(PlayScala, SbtWeb).
  aggregate(clients.map(projectToRef): _*).
  dependsOn(icdWebSharedJvm)

lazy val icdWebClient = (project in file("icd-web-client")).settings(
  scalaVersion := scalaV,
  persistLauncher := true,
  persistLauncher in Test := false,
  sourceMapsDirectories += icdWebSharedJs.base / "..",
  unmanagedSourceDirectories in Compile := Seq((scalaSource in Compile).value),
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.8.0",
    "com.lihaoyi" %%% "scalatags" % "0.5.1",
    "com.lihaoyi" %%% "upickle" % "0.2.8",
    "be.doeraene" %%% "scalajs-jquery" % "0.8.0"
  )
).enablePlugins(ScalaJSPlugin, ScalaJSPlay).
  dependsOn(icdWebSharedJs)

lazy val icdWebShared = (crossProject.crossType(CrossType.Pure) in file("icd-web-shared")).
  settings(scalaVersion := scalaV).
  jsConfigure(_ enablePlugins ScalaJSPlay).
  jsSettings(sourceMapsBase := baseDirectory.value / "..")

lazy val icdWebSharedJvm = icdWebShared.jvm
lazy val icdWebSharedJs = icdWebShared.js

// loads the Play project at sbt startup
onLoad in Global := (Command.process("project icdWebServer", _: State)) compose (onLoad in Global).value
