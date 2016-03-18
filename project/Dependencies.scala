import sbt._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Dependencies {
  val Version = "0.9"
  val ScalaVersion = "2.11.7"

  // command line dependencies
  val scopt = "com.github.scopt" %% "scopt" % "3.4.0"
  val jsonSchemaValidator = "com.github.fge" % "json-schema-validator" % "2.2.6"
  val ficus = "com.iheart" %% "ficus" % "1.2.0"
  val typesafeConfig = "com.typesafe" % "config" % "1.3.0"
  val scalaTest = "org.scalatest" %% "scalatest" % "2.2.6"
  val pegdown = "org.pegdown" % "pegdown" % "1.6.0"
  val xmlworker = "com.itextpdf.tool" % "xmlworker" % "5.5.8"
  val casbah = "org.mongodb" %% "casbah" % "2.8.2"
  val diffson = "org.gnieh" %% "diffson" % "1.1.0"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
  val logback = "ch.qos.logback" % "logback-classic" % "1.1.5"
  val scalatags = "com.lihaoyi" %% "scalatags" % "0.5.4"

  // web server dependencies
  val playScalajsScripts = "com.vmunier" %% "play-scalajs-scripts" % "0.4.0"
  val upickle = "com.lihaoyi" %% "upickle" % "0.3.8"
  val jqueryUi = "org.webjars" % "jquery-ui" % "1.11.4"
  val webjarsPlay = "org.webjars" %% "webjars-play" % "2.4.0-1"
  val bootstrap = "org.webjars" % "bootstrap" % "3.3.4"
  val bootstrapTable = "org.webjars.bower" % "bootstrap-table" % "1.7.0"

  // ScalaJS web client scala dependencies
  val clientDeps = Def.setting(Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.0",
    "com.lihaoyi" %%% "scalatags" % "0.5.4",
    "com.lihaoyi" %%% "upickle" % "0.3.8",
    "org.querki" %%% "jquery-facade" % "1.0-RC2",
    "com.github.japgolly.scalacss" %%% "core" % "0.3.1",
    "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.3.1"
  ))

  // ScalaJS client JavaScript dependencies
  val clientJsDeps = Def.setting(Seq(
    "org.webjars" % "jquery" % "2.2.1" / "jquery.js" minified "jquery.min.js",
    "org.webjars" % "jquery-ui" % "1.11.4" / "jquery-ui.min.js" dependsOn "jquery.js",
    "org.webjars" % "bootstrap" % "3.3.4" / "bootstrap.min.js" dependsOn "jquery.js",
    "org.webjars.bower" % "bootstrap-table" % "1.7.0" / "bootstrap-table.min.js",
    ProvidedJS / "resize.js" dependsOn "jquery-ui.min.js"
  ))
}

