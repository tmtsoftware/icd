import sbt._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object Dependencies {
  val Version = "0.12"
  val ScalaVersion = "2.12.3"
  val PlayVersion = "2.6.5"

  // command line dependencies
  val scopt = "com.github.scopt" %% "scopt" % "3.7.0"
  val jsonSchemaValidator = "com.github.fge" % "json-schema-validator" % "2.2.6"
  val ficus = "com.iheart" %% "ficus" % "1.4.1"
  val typesafeConfig = "com.typesafe" % "config" % "1.3.1"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1"

  val flexmarkAll = "com.vladsch.flexmark" % "flexmark-all" % "0.28.2"
  val scalaCsv = "com.github.tototoshi" %% "scala-csv" % "1.3.5"
  val itextpdf = "com.itextpdf" % "itextpdf" % "5.5.12"
  val xmlworker = "com.itextpdf.tool" % "xmlworker" % "5.5.12"
  val casbah = "org.mongodb" %% "casbah" % "3.1.1"

  val diffson = "org.gnieh" %% "diffson-spray-json" % "2.2.2"

  val sprayJson = "io.spray" %%  "spray-json" % "1.3.3"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2"
  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val scalatags = "com.lihaoyi" %% "scalatags" % "0.6.5"
  val jsoup = "org.jsoup" % "jsoup" % "1.10.3"
  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % "4.8.0.201706111038-r"

  // web server dependencies
  val scalajsScripts = "com.vmunier" %% "scalajs-scripts" % "1.1.1"

  val playJson = "com.typesafe.play" %% "play-json" % PlayVersion
  val jqueryUi = "org.webjars" % "jquery-ui" % "1.12.1"
  val webjarsPlay = "org.webjars" %% "webjars-play" % "2.6.2"
  val bootstrap = "org.webjars" % "bootstrap" % "3.3.7-1"
  val bootstrapTable = "org.webjars.bower" % "bootstrap-table" % "1.11.1"

  // ScalaJS web client scala dependencies
  val clientDeps = Def.setting(Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.3",
    "com.lihaoyi" %%% "scalatags" % "0.6.7",

    "com.typesafe.play" %%% "play-json" % PlayVersion,
    "org.querki" %%% "jquery-facade" % "1.2",
    "com.github.japgolly.scalacss" %%% "core" % "0.5.3",
    "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.5.3"
  ))

  // ScalaJS client JavaScript dependencies
  val clientJsDeps = Def.setting(Seq(
    "org.webjars" % "jquery" % "2.2.1" / "jquery.js" minified "jquery.min.js",
//    "org.webjars" % "jquery" % "3.2.1" / "jquery.js" minified "jquery.min.js",
    "org.webjars" % "jquery-ui" % "1.12.1" / "jquery-ui.min.js" dependsOn "jquery.js",
    "org.webjars" % "bootstrap" % "3.3.7-1" / "bootstrap.min.js" dependsOn "jquery.js",
    "org.webjars.bower" % "bootstrap-table" % "1.11.1" / "bootstrap-table.min.js",
    ProvidedJS / "resize.js" dependsOn "jquery-ui.min.js"
  ))
}

