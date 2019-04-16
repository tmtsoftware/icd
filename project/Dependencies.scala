import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._


object Dependencies {
  val Version = "0.13"
  val ScalaVersion = "2.12.8"
  val PlayVersion = "2.7.0"
  val PlayJsonVersion = "2.7.2"
  val ScalaTagsVersion = "0.6.7"
  val BootstrapVersion = "3.3.7-1"
  val JQueryVersion = "2.2.1"
  val JQueryUiVersion = "1.12.1"
  val BootstrapTableVersion = "1.14.1"

  // command line dependencies
  val scopt = "com.github.scopt" %% "scopt" % "3.7.1"
//  val jsonSchemaValidator = "com.github.fge" % "json-schema-validator" % "2.2.6"
  val jsonSchemaValidator = "com.github.everit-org.json-schema" % "org.everit.json.schema" % "1.11.1"
  val ficus = "com.iheart" %% "ficus" % "1.4.4"
  val typesafeConfig = "com.typesafe" % "config" % "1.3.3"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"

  val flexmarkAll = "com.vladsch.flexmark" % "flexmark-all" % "0.35.2"
  val scalaCsv = "com.github.tototoshi" %% "scala-csv" % "1.3.5"
  val itextpdf = "com.itextpdf" % "itextpdf" % "5.5.13"
  val xmlworker = "com.itextpdf.tool" % "xmlworker" % "5.5.13"
  val casbah = "org.mongodb" %% "casbah" % "3.1.1"

  val diffson = "org.gnieh" %% "diffson-spray-json" % "3.1.0"

  val sprayJson = "io.spray" %% "spray-json" % "1.3.5"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val scalatags = "com.lihaoyi" %% "scalatags" % ScalaTagsVersion
  val jsoup = "org.jsoup" % "jsoup" % "1.11.3"
  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % "5.2.0.201812061821-r"

  // web server dependencies
  val scalajsScripts = "com.vmunier" %% "scalajs-scripts" % "1.1.2"

  val playJson = "com.typesafe.play" %% "play-json" % PlayJsonVersion
  val jqueryUi = "org.webjars" % "jquery-ui" % JQueryUiVersion
  val webjarsPlay = "org.webjars" %% "webjars-play" % "2.7.0"
  // Note: Updating to bootstrap-4 could be a lot of work...
  val bootstrap = "org.webjars" % "bootstrap" % BootstrapVersion
  val bootstrapTable = "org.webjars.bower" % "bootstrap-table" % BootstrapTableVersion

  // ScalaJS web client scala dependencies
  val clientDeps = Def.setting(Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.6",
    "com.lihaoyi" %%% "scalatags" % ScalaTagsVersion,

    "com.typesafe.play" %%% "play-json" % PlayJsonVersion,
    "org.querki" %%% "jquery-facade" % "1.2",
    "com.github.japgolly.scalacss" %%% "core" % "0.5.5",
    "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.5.5"
  ))

}

