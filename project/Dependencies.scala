import sbt.*
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*

object Dependencies {
  val Version = "3.0.1"
  val ScalaVersion    = "2.13.12"
  val PekkoVersion    = "1.0.2"
  val PlayJsonVersion = "3.0.2"
  val ScalaTagsVersion  = "0.12.0"
  val ScalaJsDomVersion = "2.8.0"
  val ScalaCssVersion       = "1.0.0"
  val BootstrapVersion      = "5.3.2"
  val JQueryVersion         = "3.7.1"
  val JQueryUiVersion       = "1.13.2"
  val BootstrapTableVersion = "1.22.2"
  val BootstrapIconsVersion = "1.11.3"

  val pekkoActorTyped           = "org.apache.pekko" %% "pekko-actor-typed"           % PekkoVersion
  val pekkoActor                = "org.apache.pekko" %% "pekko-actor"                 % PekkoVersion
  val pekkoStream               = "org.apache.pekko" %% "pekko-stream"                % PekkoVersion
  val pekkoSerializationJackson = "org.apache.pekko" %% "pekko-serialization-jackson" % PekkoVersion

  // command line dependencies
  val scopt               = "com.github.scopt"                 %% "scopt"                  % "4.1.0"
  val jsonSchemaValidator = "com.github.everit-org.json-schema" % "org.everit.json.schema" % "1.14.4"
  val ficus               = "com.iheart"                       %% "ficus"                  % "1.5.2"
  val typesafeConfig      = "com.typesafe"                      % "config"                 % "1.4.3"
  val scalaTest           = "org.scalatest"                    %% "scalatest"              % "3.2.18"

  val flexmarkAll = "com.vladsch.flexmark"  % "flexmark-all" % "0.64.8"
  val scalaCsv    = "com.github.tototoshi" %% "scala-csv"    % "1.3.10"

  val itext7Core  = "com.itextpdf"       % "itext-core"   % "8.0.3" pomOnly ()
  val html2pdf    = "com.itextpdf"       % "html2pdf"     % "5.0.3"
  val jlatexmath  = "org.scilab.forge"   % "jlatexmath"   % "1.0.7"
  val commonsIo   = "commons-io"         % "commons-io"   % "2.15.1"
  val commonsText = "org.apache.commons" % "commons-text" % "1.11.0"

  val plantuml = "net.sourceforge.plantuml" % "plantuml"  % "1.2024.3"
  val graphDot = ("org.scala-graph"        %% "graph-dot" % "1.13.3") // XXX TODO update to 2.0.0, breaking changes

  val reactivemongo               = "org.reactivemongo" %% "reactivemongo"                  % "1.1.0.pekko-RC12"
  val reactivemongoPlayJsonCompat = "org.reactivemongo" %% "reactivemongo-play-json-compat" % "1.1.0.play210-RC12"

  // XXX Need at one point to fix indirect dependency conflict (still needed?)
  val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.16.1"

  val diffson = "org.gnieh" %% "diffson-play-json" % "4.5.0"

  val scalaLogging   = "com.typesafe.scala-logging" %% "scala-logging"    % "3.9.5"
  val scalatags      = "com.lihaoyi"                %% "scalatags"        % ScalaTagsVersion
  val osLib          = "com.lihaoyi"                %% "os-lib"           % "0.9.3"
  val jsoup          = "org.jsoup"                   % "jsoup"            % "1.17.2"
  val jgit           = "org.eclipse.jgit"            % "org.eclipse.jgit" % "6.8.0.202311291450-r"
  val swaggerParser  = "io.swagger.parser.v3"        % "swagger-parser"   % "2.1.20"
  val playJson       = "org.playframework"          %% "play-json"        % PlayJsonVersion
  val jqueryUi       = "org.webjars"                 % "jquery-ui"        % JQueryUiVersion
  val webjarsPlay    = "org.webjars"                %% "webjars-play"     % "3.0.1"
  val bootstrap      = "org.webjars.npm"             % "bootstrap"        % BootstrapVersion
  val bootstrapTable = "org.webjars.npm"             % "bootstrap-table"  % BootstrapTableVersion
  val bootstrapIcons = "org.webjars.npm"             % "bootstrap-icons"  % BootstrapIconsVersion
  val swaggerUi      = "org.webjars"                 % "swagger-ui"       % "5.10.3"

  // ScalaJS web client scala dependencies
  val clientDeps = Def.setting(
    Seq(
      "org.scala-js"      %%% "scalajs-dom"                 % ScalaJsDomVersion,
      "com.lihaoyi"       %%% "scalatags"                   % ScalaTagsVersion,
      "org.playframework" %%% "play-json"                   % PlayJsonVersion,
      "org.scala-js"      %%% "scala-js-macrotask-executor" % "1.1.0"
    )
  )

}
