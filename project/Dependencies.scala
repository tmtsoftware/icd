import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Dependencies {
  val Version               = "1.3.2"
  val ScalaVersion          = "2.13.1"
  val AkkaVersion           = "2.6.6"
  val PlayVersion           = "2.8.1"
//  val PlayJsonVersion       = "2.9.0"
  val PlayJsonVersion       = "2.8.1"
  val ScalaTagsVersion      = "0.8.6"
  val BootstrapVersion      = "3.4.1"
  val JQueryVersion         = "2.2.1"
  val JQueryUiVersion       = "1.12.1"
  val BootstrapTableVersion = "1.15.5"

  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion
  val akkaActorTyped = "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion
  val akkaActor = "com.typesafe.akka" %% "akka-actor" % AkkaVersion
  val akkaStream = "com.typesafe.akka" %% "akka-stream" % AkkaVersion

  // command line dependencies
  val scopt               = "com.github.scopt"                  %% "scopt"                 % "3.7.1"
  val jsonSchemaValidator = "com.github.everit-org.json-schema" % "org.everit.json.schema" % "1.12.1"
  val ficus               = "com.iheart"                        %% "ficus"                 % "1.4.7"
  val typesafeConfig      = "com.typesafe"                      % "config"                 % "1.4.0"
  val scalaTest           = "org.scalatest"                     %% "scalatest"             % "3.1.1"

  val flexmarkAll = "com.vladsch.flexmark" % "flexmark-all" % "0.60.2"
  val scalaCsv    = "com.github.tototoshi" %% "scala-csv"   % "1.3.6"

  val itext7Core  = "com.itextpdf"         % "itext7-core"  % "7.1.11" pomOnly ()
  val html2pdf    = "com.itextpdf"         % "html2pdf"     % "3.0.0"
  val jlatexmath = "org.scilab.forge" % "jlatexmath"  % "1.0.7"
  val commonsIo = "commons-io" % "commons-io" % "2.7"

  val reactivemongo               = "org.reactivemongo" %% "reactivemongo"                  % "0.20.3"
  val play2Reactivemongo          = "org.reactivemongo" %% "play2-reactivemongo"            % "0.20.3-play28"
  val reactivemongoPlayJsonCompat = "org.reactivemongo" %% "reactivemongo-play-json-compat" % "0.20.3-play28"

  val diffson = "org.gnieh" %% "diffson-play-json" % "4.0.2"

  val scalaLogging   = "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.2"
  val logbackClassic = "ch.qos.logback"             % "logback-classic"  % "1.2.3"
  val scalatags      = "com.lihaoyi"                %% "scalatags"       % ScalaTagsVersion
  val jsoup          = "org.jsoup"                  % "jsoup"            % "1.12.2"
  val jgit           = "org.eclipse.jgit"           % "org.eclipse.jgit" % "5.6.1.202002131546-r"

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
