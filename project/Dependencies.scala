import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Dependencies {
  val Version               = "2.2.2"
  val ScalaVersion          = "2.13.10"
  val AkkaVersion           = "2.7.0"
  val PlayVersion           = "2.8.13"
  val PlayJsonVersion       = "2.9.3"
  val ScalaTagsVersion      = "0.11.1"
  val ScalaJsDomVersion     = "2.2.0"
  val ScalaCssVersion       = "1.0.0"
  val BootstrapVersion      = "5.2.2"
  val JQueryVersion         = "3.6.1"
  val JQueryUiVersion       = "1.13.2"
  val BootstrapTableVersion = "1.21.1"
  val BootstrapIconsVersion = "1.9.1"

  //  val akkaSlf4j      = "com.typesafe.akka" %% "akka-slf4j"       % AkkaVersion
  val akkaActorTyped = "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion
  val akkaActor      = "com.typesafe.akka" %% "akka-actor"       % AkkaVersion
  val akkaStream     = "com.typesafe.akka" %% "akka-stream"      % AkkaVersion
  val akkaSerializationJackson = "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion


  // command line dependencies
  val scopt               = "com.github.scopt"                 %% "scopt"                  % "4.1.0"
  val jsonSchemaValidator = "com.github.everit-org.json-schema" % "org.everit.json.schema" % "1.14.1"
  val ficus               = "com.iheart"                       %% "ficus"                  % "1.5.2"
  val typesafeConfig      = "com.typesafe"                      % "config"                 % "1.4.2"
  val scalaTest           = "org.scalatest"                    %% "scalatest"              % "3.2.14"

  val flexmarkAll = "com.vladsch.flexmark"  % "flexmark-all" % "0.64.0"
  val scalaCsv    = "com.github.tototoshi" %% "scala-csv"    % "1.3.10"

  val sjsonnet = "com.databricks" %% "sjsonnet" % "0.4.3"

  val itext7Core  = "com.itextpdf"       % "itext7-core"  % "7.2.4" pomOnly ()
  val html2pdf    = "com.itextpdf"       % "html2pdf"     % "4.0.4"
  val jlatexmath  = "org.scilab.forge"   % "jlatexmath"   % "1.0.7"
  val commonsIo   = "commons-io"         % "commons-io"   % "2.11.0"
  val commonsText = "org.apache.commons" % "commons-text" % "1.10.0"

  val plantuml = "net.sourceforge.plantuml" % "plantuml"  % "1.2022.12"
  val graphDot = "org.scala-graph"         %% "graph-dot" % "1.13.3"

  val reactivemongo               = "org.reactivemongo" %% "reactivemongo"                  % "1.1.0-RC6"
  val reactivemongoPlayJsonCompat = "org.reactivemongo" %% "reactivemongo-play-json-compat" % "1.1.0-play29-RC6"
  //  // XXX Need to fix indirect dependency conflict
  val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.14.0"

  val diffson = "org.gnieh" %% "diffson-play-json" % "4.3.0"

  val scalaLogging   = "com.typesafe.scala-logging" %% "scala-logging"    % "3.9.5"
//  val logbackClassic = "ch.qos.logback"              % "logback-classic"  % "1.2.11"
  val scalatags      = "com.lihaoyi"                %% "scalatags"        % ScalaTagsVersion
  val jsoup          = "org.jsoup"                   % "jsoup"            % "1.15.3"
  val jgit           = "org.eclipse.jgit"            % "org.eclipse.jgit" % "6.3.0.202209071007-r"
  val swaggerParser  = "io.swagger.parser.v3"        % "swagger-parser"   % "2.1.8"
  val playJson       = "com.typesafe.play"          %% "play-json"        % PlayJsonVersion
  val jqueryUi       = "org.webjars"                 % "jquery-ui"        % JQueryUiVersion
  val webjarsPlay    = "org.webjars"                %% "webjars-play"     % "2.8.18"
  val bootstrap      = "org.webjars.npm"             % "bootstrap"        % BootstrapVersion
  val bootstrapTable = "org.webjars.npm"             % "bootstrap-table"  % BootstrapTableVersion
  val bootstrapIcons = "org.webjars.npm"             % "bootstrap-icons"  % BootstrapIconsVersion
  val swaggerUi      = "org.webjars"                 % "swagger-ui"       % "4.15.5"

  // ScalaJS web client scala dependencies
  val clientDeps = Def.setting(
    Seq(
      "org.scala-js"                 %%% "scalajs-dom"                 % ScalaJsDomVersion,
      "com.lihaoyi"                  %%% "scalatags"                   % ScalaTagsVersion,
      "com.typesafe.play"            %%% "play-json"                   % PlayJsonVersion,
      "com.github.japgolly.scalacss" %%% "core"                        % ScalaCssVersion,
      "com.github.japgolly.scalacss" %%% "ext-scalatags"               % ScalaCssVersion,
      "org.scala-js"                 %%% "scala-js-macrotask-executor" % "1.1.0"
    )
  )

}
