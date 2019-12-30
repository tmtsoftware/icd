import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Dependencies {
  val Version               = "0.17"
  val ScalaVersion          = "2.13.1"
  val AkkaVersion          = "2.5.25"
  val PlayVersion           = "2.8.0"
  val PlayJsonVersion       = "2.8.1"
  val ScalaTagsVersion      = "0.7.0"
  val BootstrapVersion      = "3.4.1"
  val JQueryVersion         = "2.2.1"
  val JQueryUiVersion       = "1.12.1"
  val BootstrapTableVersion = "1.15.5"

  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion

  // command line dependencies
  val scopt = "com.github.scopt" %% "scopt" % "3.7.1"
  val jsonSchemaValidator = "com.github.everit-org.json-schema" % "org.everit.json.schema" % "1.12.0"
  val ficus               = "com.iheart"                        %% "ficus"                 % "1.4.7"
  val typesafeConfig      = "com.typesafe"                      % "config"                 % "1.3.4"
  val scalaTest           = "org.scalatest"                     %% "scalatest"             % "3.0.8"

  val flexmarkAll = "com.vladsch.flexmark" % "flexmark-all" % "0.50.40"
  val scalaCsv    = "com.github.tototoshi" %% "scala-csv"   % "1.3.6"
  val itextpdf    = "com.itextpdf"         % "itextpdf"     % "5.5.13.1"
  val xmlworker   = "com.itextpdf.tool"    % "xmlworker"    % "5.5.13.1"

  val reactivemongo = "org.reactivemongo" %% "reactivemongo" % "0.19.7"
  val play2Reactivemongo = "org.reactivemongo" %% "play2-reactivemongo" % "0.19.7-play28"
  val reactivemongoPlayJsonCompat = "org.reactivemongo" %% "reactivemongo-play-json-compat" % "0.19.7-play28"

  val diffson = "org.gnieh" %% "diffson-play-json" % "4.0.0"

  val scalaLogging   = "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.2"
  val logbackClassic = "ch.qos.logback"             % "logback-classic"  % "1.2.3"
  val scalatags      = "com.lihaoyi"                %% "scalatags"       % ScalaTagsVersion
  val jsoup          = "org.jsoup"                  % "jsoup"            % "1.12.1"
  val jgit           = "org.eclipse.jgit"           % "org.eclipse.jgit" % "5.5.0.201909110433-r"

  // web server dependencies
  val scalajsScripts = "com.vmunier" %% "scalajs-scripts" % "1.1.4"

  val playJson    = "com.typesafe.play" %% "play-json"    % PlayJsonVersion
  val jqueryUi    = "org.webjars"       % "jquery-ui"     % JQueryUiVersion
  val webjarsPlay = "org.webjars"       %% "webjars-play" % "2.8.0"
  // Note: Updating to bootstrap-4 could be a lot of work...
  val bootstrap      = "org.webjars"       % "bootstrap"       % BootstrapVersion
  val bootstrapTable = "org.webjars.bower" % "bootstrap-table" % BootstrapTableVersion

  // ScalaJS web client scala dependencies
  val clientDeps = Def.setting(
    Seq(
      "org.scala-js"                 %%% "scalajs-dom"   % "0.9.7",
      "com.lihaoyi"                  %%% "scalatags"     % ScalaTagsVersion,
      "com.typesafe.play"            %%% "play-json"     % PlayJsonVersion,
      "com.github.japgolly.scalacss" %%% "core"          % "0.6.0-RC1",
      "com.github.japgolly.scalacss" %%% "ext-scalatags" % "0.6.0-RC1"
    )
  )

}
