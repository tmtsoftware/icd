import com.typesafe.sbt.packager.archetypes.JavaAppPackaging

import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys._
import sbt._
import sbt.Project.projectToRef
import com.typesafe.sbt.packager.docker._

val Version = "0.1-SNAPSHOT"
val ScalaVersion = "2.11.6"

// Basic settings
val buildSettings = Seq(
  organization := "org.tmt",
  organizationName := "TMT",
  organizationHomepage := Some(url("http://www.tmt.org")),
  version := Version,
  scalaVersion := ScalaVersion,
  crossPaths := true,
  parallelExecution in Test := false,
  fork := true,
  resolvers += Resolver.typesafeRepo("releases"),
  resolvers += Resolver.sonatypeRepo("releases"),
  resolvers += sbtResolver.value
)

// Automatic code formatting
def formattingPreferences: FormattingPreferences =
  FormattingPreferences()
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(DoubleIndentClassDeclaration, true)

lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
  ScalariformKeys.preferences in Compile := formattingPreferences,
  ScalariformKeys.preferences in Test := formattingPreferences
)

// Using java8
lazy val defaultSettings = buildSettings ++ formatSettings ++ Seq(
  scalacOptions ++= Seq("-target:jvm-1.8", "-encoding", "UTF-8", "-feature", "-deprecation", "-unchecked"),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked", "-Xlint:deprecation")
)

// Customize the Docker install
lazy val dockerSettings = Seq(
  maintainer := "TMT Software",
  dockerExposedPorts := Seq(9000),
  dockerBaseImage := "java:8"
)

lazy val clients = Seq(icdWebClient)

def compile(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
def provided(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
def test(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")

// dependencies
val scopt = "com.github.scopt" %% "scopt" % "3.3.0"
val jsonSchemaValidator = "com.github.fge" % "json-schema-validator" % "2.2.6"
val ficus = "net.ceedubs" % "ficus_2.11" % "1.1.2"
val typesafeConfig = "com.typesafe" % "config" % "1.2.1"
val scalaTest = "org.scalatest" %% "scalatest" % "2.1.5"
val pegdown = "org.pegdown" % "pegdown" % "1.4.2"
val xmlworker = "com.itextpdf.tool" % "xmlworker" % "5.5.6"
val casbah = "org.mongodb" %% "casbah" % "2.8.2"
val diffson = "org.gnieh" %% "diffson" % "0.3"
val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2"
val logback = "ch.qos.logback" % "logback-classic" % "1.1.1"
val scalatags = "com.lihaoyi" %% "scalatags" % "0.5.2"

// Root of the multi-project build
lazy val root = (project in file("."))
  .aggregate(icd, `icd-db`)

// core project, implements validation of ICD files against JSON schema files, icd command line tool
lazy val icd = project
  .enablePlugins(JavaAppPackaging)
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(jsonSchemaValidator, scopt, typesafeConfig, ficus, pegdown, xmlworker, diffson, scalaLogging, logback) ++
      test(scalaTest)
  )

// adds MongoDB database support, ICD versioning, queries
lazy val `icd-db` = project
  .enablePlugins(JavaAppPackaging)
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(casbah, scalatags) ++
      test(scalaTest)
  ) dependsOn(icd, icdWebSharedJvm)


// -- Play/ScalaJS parts below --


// a Play framework based web server that goes between icd-db and the web client
lazy val icdWebServer = (project in file("icd-web/icd-web-server"))
  .settings(defaultSettings: _*)
  .settings(dockerSettings: _*)
  .settings(
    scalaJSProjects := clients,
    pipelineStages := Seq(scalaJSProd, gzip),
    includeFilter in(Assets, LessKeys.less) := "*.less",
    libraryDependencies ++= Seq(
      filters,
      "org.tmt" %% "icd-db" % "0.1-SNAPSHOT",
      "com.vmunier" %% "play-scalajs-scripts" % "0.3.0",
      "com.lihaoyi" %%% "upickle" % "0.2.8",
      "org.webjars" % "jquery-ui" % "1.11.4",
      "org.webjars" %% "webjars-play" % "2.4.0-1",
      "org.webjars" % "bootstrap" % "3.3.4",
      "org.webjars.bower" % "bootstrap-table" % "1.7.0",
      specs2 % Test
    )
  )
  .enablePlugins(PlayScala, SbtWeb, DockerPlugin)
  .aggregate(clients.map(projectToRef): _*)
  .dependsOn(`icd-db`)

// a Scala.js based web client that talks to the Play server
lazy val icdWebClient = (project in file("icd-web/icd-web-client")).settings(
  scalaVersion := ScalaVersion,
  persistLauncher := true,
  persistLauncher in Test := false,
  sourceMapsDirectories += icdWebSharedJs.base / "..",
  unmanagedSourceDirectories in Compile := Seq((scalaSource in Compile).value),
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.8.1",
    "com.lihaoyi" %%% "scalatags" % "0.5.2",
    "com.lihaoyi" %%% "upickle" % "0.2.8",
    "org.querki" %%% "jquery-facade" % "0.6", // includes jquery webjar!
    "com.github.japgolly.scalacss" %%% "core" % "0.3.0",
    "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.3.0"
  ),
  skip in packageJSDependencies := false,
  jsDependencies ++= Seq(
    "org.webjars" % "jquery-ui" % "1.11.4" / "jquery-ui.min.js" dependsOn "jquery.js",
    "org.webjars" % "bootstrap" % "3.3.4" / "bootstrap.min.js" dependsOn "jquery.js",
    "org.webjars.bower" % "bootstrap-table" % "1.7.0" / "bootstrap-table.min.js",
    ProvidedJS / "resize.js" dependsOn "jquery-ui.min.js"
  )
).settings(formatSettings: _*)
  .enablePlugins(ScalaJSPlugin, ScalaJSPlay)
  .dependsOn(icdWebSharedJs)


// contains simple case classes used for data transfer that are shared between the client and server
lazy val icdWebShared = (crossProject.crossType(CrossType.Pure) in file("icd-web/icd-web-shared"))
  .settings(scalaVersion := ScalaVersion)
  .settings(formatSettings: _*)
  .jsConfigure(_ enablePlugins ScalaJSPlay)
  .jsSettings(sourceMapsBase := baseDirectory.value / "..")

lazy val icdWebSharedJvm = icdWebShared.jvm
lazy val icdWebSharedJs = icdWebShared.js

// loads the Play project at sbt startup
onLoad in Global := (Command.process("project icdWebServer", _: State)) compose (onLoad in Global).value
