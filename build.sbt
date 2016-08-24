import sbt.Keys._
import sbt._
import sbt.Project.projectToRef

import Dependencies._
import Settings._

def compile(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
def test(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")

lazy val clients = Seq(icdWebClient)

// Root of the multi-project build
lazy val root = (project in file("."))
  .aggregate(icd, `icd-db`, `icd-git`, icdWebServer)
  .settings(name := "ICD")

// Core project, implements validation of ICD model files against JSON schema files, icd command line tool
lazy val icd = project
  .enablePlugins(JavaAppPackaging)
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(jsonSchemaValidator, scopt, scalatags, typesafeConfig, ficus, pegdown, itextpdf, xmlworker, diffson, sprayJson, scalaLogging, logback, jsoup) ++
      test(scalaTest)
  ) dependsOn icdWebSharedJvm

// Adds MongoDB database support, ICD versioning, queries, icd-db command line tool
lazy val `icd-db` = project
  .enablePlugins(JavaAppPackaging)
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(casbah) ++
      test(scalaTest)
  ) dependsOn icd


// Adds support for working with ICD model file repositories on GitHub, ICD version management, icd-github tool
lazy val `icd-git` = project
  .enablePlugins(JavaAppPackaging)
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++=
    compile(jgit) ++
      test(scalaTest)
  ) dependsOn(icd, `icd-db`)


// -- Play/ScalaJS parts below --


// a Play framework based web server that goes between icd-db and the web client
lazy val icdWebServer = (project in file("icd-web/icd-web-server"))
  .settings(defaultSettings: _*)
  .settings(dockerSettings: _*)
  .settings(
    scalaJSProjects := clients,
    pipelineStages := Seq(scalaJSProd, gzip),
    includeFilter in(Assets, LessKeys.less) := "*.less",
    libraryDependencies ++=
      compile(filters, playScalajsScripts, upickle, jqueryUi, webjarsPlay, bootstrap, bootstrapTable) ++
        test(specs2)
  )
  .enablePlugins(PlayScala, SbtWeb, DockerPlugin)
  .aggregate(clients.map(projectToRef): _*)
  .dependsOn(`icd-db`, `icd-git`)

// a Scala.js based web client that talks to the Play server
lazy val icdWebClient = (project in file("icd-web/icd-web-client")).settings(
  scalaVersion := Dependencies.ScalaVersion,
  persistLauncher := true,
  persistLauncher in Test := false,
  unmanagedSourceDirectories in Compile := Seq((scalaSource in Compile).value),
  libraryDependencies ++= clientDeps.value,
  skip in packageJSDependencies := false,
  jsDependencies ++= clientJsDeps.value
)//.settings(formatSettings: _*)
  .enablePlugins(ScalaJSPlugin, ScalaJSPlay)
  .dependsOn(icdWebSharedJs)

// contains simple case classes used for data transfer that are shared between the client and server
lazy val icdWebShared = (crossProject.crossType(CrossType.Pure) in file("icd-web/icd-web-shared"))
  .settings(scalaVersion := Dependencies.ScalaVersion)
//  .settings(formatSettings: _*)
  .jsConfigure(_ enablePlugins ScalaJSPlay)

lazy val icdWebSharedJvm = icdWebShared.jvm
lazy val icdWebSharedJs = icdWebShared.js

// loads the Play project at sbt startup
onLoad in Global := (Command.process("project icdWebServer", _: State)) compose (onLoad in Global).value
