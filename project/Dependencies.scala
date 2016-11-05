import sbt._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Dependencies {
  val Version = "0.9"
  val ScalaVersion = "2.11.8"

  // command line dependencies
  val scopt = "com.github.scopt" %% "scopt" % "3.5.0"
  val jsonSchemaValidator = "com.github.fge" % "json-schema-validator" % "2.2.6"
  val ficus = "com.iheart" %% "ficus" % "1.2.6"
  val typesafeConfig = "com.typesafe" % "config" % "1.3.0"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.0"
  val pegdown = "org.pegdown" % "pegdown" % "1.6.0"
  val itextpdf = "com.itextpdf" % "itextpdf" % "5.5.10"
  val xmlworker = "com.itextpdf.tool" % "xmlworker" % "5.5.10"
  val casbah = "org.mongodb" %% "casbah" % "3.1.1"
  val diffson = "org.gnieh" %% "diffson" % "1.1.0" // tried newer version, but changes in API means more work needed on this side...
  val sprayJson = "io.spray" %%  "spray-json" % "1.3.2"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0"
  val logback = "ch.qos.logback" % "logback-classic" % "1.1.7"
  val scalatags = "com.lihaoyi" %% "scalatags" % "0.6.1"
  val jsoup = "org.jsoup" % "jsoup" % "1.9.2"
//  val buhtig = "net.caoticode.buhtig" %% "buhtig" % "0.3.1"
  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % "4.4.1.201607150455-r" // EDL (new-style BSD)

  // web server dependencies
  val playScalajsScripts = "com.vmunier" %% "play-scalajs-scripts" % "0.5.0"
  val upickle = "com.lihaoyi" %% "upickle" % "0.4.1"
  val jqueryUi = "org.webjars" % "jquery-ui" % "1.12.0"
  val webjarsPlay = "org.webjars" %% "webjars-play" % "2.4.0-1"
  val bootstrap = "org.webjars" % "bootstrap" % "3.3.7"
  val bootstrapTable = "org.webjars.bower" % "bootstrap-table" % "1.11.0"

  // ScalaJS web client scala dependencies
  val clientDeps = Def.setting(Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.1",
    "com.lihaoyi" %%% "scalatags" % "0.6.1",
    "com.lihaoyi" %%% "upickle" % "0.4.1",
    "org.querki" %%% "jquery-facade" % "1.0-RC6",
    "com.github.japgolly.scalacss" %%% "core" % "0.4.1",
    "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.4.1"
  ))

  // ScalaJS client JavaScript dependencies
  val clientJsDeps = Def.setting(Seq(
    "org.webjars" % "jquery" % "3.1.0" / "jquery.js" minified "jquery.min.js",
    "org.webjars" % "jquery-ui" % "1.12.0" / "jquery-ui.min.js" dependsOn "jquery.js",
    "org.webjars" % "bootstrap" % "3.3.7" / "bootstrap.min.js" dependsOn "jquery.js",
    "org.webjars.bower" % "bootstrap-table" % "1.11.0" / "bootstrap-table.min.js",
    ProvidedJS / "resize.js" dependsOn "jquery-ui.min.js"
  ))
}

