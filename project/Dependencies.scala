import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Dependencies {
  val Version               = "2.1.3"
  val ScalaVersion          = "2.13.8"
  val AkkaVersion           = "2.6.18"
  val PlayVersion           = "2.8.12"
  val PlayJsonVersion       = "2.9.2"
  val ScalaTagsVersion      = "0.11.0"
  val ScalaJsDomVersion     = "2.1.0"
  val ScalaCssVersion       = "1.0.0"
  val BootstrapVersion      = "3.4.1"
  val JQueryVersion         = "2.2.1"
  val JQueryUiVersion       = "1.12.1"
  val BootstrapTableVersion = "1.15.5"

//  val akkaSlf4j      = "com.typesafe.akka" %% "akka-slf4j"       % AkkaVersion
  val akkaActorTyped = "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion
  val akkaActor      = "com.typesafe.akka" %% "akka-actor"       % AkkaVersion
  val akkaStream     = "com.typesafe.akka" %% "akka-stream"      % AkkaVersion

  // command line dependencies
  val scopt               = "com.github.scopt"                 %% "scopt"                  % "4.0.1"
  val jsonSchemaValidator = "com.github.everit-org.json-schema" % "org.everit.json.schema" % "1.14.0"
  val ficus               = "com.iheart"                       %% "ficus"                  % "1.5.1"
  val typesafeConfig      = "com.typesafe"                      % "config"                 % "1.4.1"
  val scalaTest           = "org.scalatest"                    %% "scalatest"              % "3.2.10"

  val flexmarkAll = "com.vladsch.flexmark"  % "flexmark-all" % "0.62.2"
  val scalaCsv    = "com.github.tototoshi" %% "scala-csv"    % "1.3.10"

  val itext7Core = "com.itextpdf"             % "itext7-core" % "7.2.0" pomOnly ()
  val html2pdf   = "com.itextpdf"             % "html2pdf"    % "4.0.0"
  val jlatexmath = "org.scilab.forge"         % "jlatexmath"  % "1.0.7"
  val commonsIo  = "commons-io"               % "commons-io"  % "2.11.0"
  val plantuml   = "net.sourceforge.plantuml" % "plantuml"    % "1.2021.16"
  val graphDot   = "org.scala-graph"         %% "graph-dot"   % "1.13.3"

  val reactivemongo               = "org.reactivemongo" %% "reactivemongo"                  % "1.0.10"
  val play2Reactivemongo          = "org.reactivemongo" %% "play2-reactivemongo"            % "1.1.0-play28-RC2"
  val reactivemongoPlayJsonCompat = "org.reactivemongo" %% "reactivemongo-play-json-compat" % "1.1.0-play29-RC2"
//  // XXX Need to fix indirect dependency conflict
  val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.1"

  val diffson = "org.gnieh" %% "diffson-play-json" % "4.1.1"

  val scalaLogging   = "com.typesafe.scala-logging" %% "scala-logging"    % "3.9.4"
  val logbackClassic = "ch.qos.logback"              % "logback-classic"  % "1.2.10"
  val scalatags      = "com.lihaoyi"                %% "scalatags"        % ScalaTagsVersion
  val jsoup          = "org.jsoup"                   % "jsoup"            % "1.14.3"
  val jgit           = "org.eclipse.jgit"            % "org.eclipse.jgit" % "6.0.0.202111291000-r"
  val swaggerParser  = "io.swagger.parser.v3"        % "swagger-parser"   % "2.0.29"
  val playJson       = "com.typesafe.play"          %% "play-json"        % PlayJsonVersion
  val jqueryUi       = "org.webjars"                 % "jquery-ui"        % JQueryUiVersion
  val webjarsPlay    = "org.webjars"                %% "webjars-play"     % "2.8.8"
  // Note: Updating to bootstrap-4 could be a lot of work...
  val bootstrap      = "org.webjars"       % "bootstrap"       % BootstrapVersion
  val bootstrapTable = "org.webjars.bower" % "bootstrap-table" % BootstrapTableVersion

  // ScalaJS web client scala dependencies
  val clientDeps = Def.setting(
    Seq(
      "org.scala-js"                 %%% "scalajs-dom"                 % ScalaJsDomVersion,
      "com.lihaoyi"                  %%% "scalatags"                   % ScalaTagsVersion,
      "com.typesafe.play"            %%% "play-json"                   % PlayJsonVersion,
      "com.github.japgolly.scalacss" %%% "core"                        % ScalaCssVersion,
      "com.github.japgolly.scalacss" %%% "ext-scalatags"               % ScalaCssVersion,
      "org.scala-js"                 %%% "scala-js-macrotask-executor" % "1.0.0"
    )
  )

}
