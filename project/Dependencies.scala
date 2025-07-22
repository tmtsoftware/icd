import sbt.*
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*

//noinspection TypeAnnotation
object Dependencies {
  val Version = "3.2.0"
  val ScalaVersion    = "3.7.1"
  val PekkoVersion    = "1.1.4"
//  val PlayJsonVersion = "3.0.4"
  val PlayJsonVersion = "3.1.0-M1"
  val ScalaTagsVersion  = "0.13.1"
  val ScalaJsDomVersion = "2.8.0"
  val BootstrapVersion      = "5.3.7"
  val JQueryVersion         = "3.7.1"
  val JQueryUiVersion       = "1.14.1"
  val BootstrapTableVersion = "1.24.1"
  val BootstrapIconsVersion = "1.13.1"

  val pekkoActorTyped           = "org.apache.pekko" %% "pekko-actor-typed"           % PekkoVersion
  val pekkoActor                = "org.apache.pekko" %% "pekko-actor"                 % PekkoVersion
  val pekkoStream               = "org.apache.pekko" %% "pekko-stream"                % PekkoVersion
  val pekkoSerializationJackson = "org.apache.pekko" %% "pekko-serialization-jackson" % PekkoVersion

  // command line dependencies
  val scopt               = "com.github.scopt"                 %% "scopt"                  % "4.1.0"
  val jsonSchemaValidator = "com.github.everit-org.json-schema" % "org.everit.json.schema" % "1.14.4"
  val ficus               = "com.iheart"                       %% "ficus"                  % "1.5.2"
  val typesafeConfig      = "com.typesafe"                      % "config"                 % "1.4.3"
  val scalaTest           = "org.scalatest"                    %% "scalatest"              % "3.2.19"

  val flexmarkAll = "com.vladsch.flexmark"  % "flexmark-all" % "0.64.8"
  val scalaCsv    = "com.github.tototoshi" %% "scala-csv"    % "2.0.0"

  val itext7Core  = "com.itextpdf"       % "itext-core"   % "9.2.0" pomOnly ()
  val html2pdf    = "com.itextpdf"       % "html2pdf"     % "6.2.0"
  val jlatexmath  = "org.scilab.forge"   % "jlatexmath"   % "1.0.7"
  val commonsIo   = "commons-io"         % "commons-io"   % "2.19.0"
  val commonsText = "org.apache.commons" % "commons-text" % "1.13.1"

  val plantuml = "net.sourceforge.plantuml" % "plantuml"  % "1.2025.4"
  val graphDot = ("org.scala-graph"        %% "graph-dot" % "2.0.0")

  val reactivemongo               = "org.reactivemongo" %% "reactivemongo"                  % "1.1.0-pekko.RC15"
  val reactivemongoPlayJsonCompat = "org.reactivemongo" %% "reactivemongo-play-json-compat" % "1.1.0-play210.RC15"

  // XXX Need at one point to fix indirect dependency conflict (still needed?)
  val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.19.1"

  val diffson = "org.gnieh" %% "diffson-play-json" % "4.6.0"

  // Dual license: Either, Eclipse Public License v1.0 or GNU Lesser General Public License version 2.1
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.5.17"

  val scalaLogging   = "com.typesafe.scala-logging" %% "scala-logging"    % "3.9.5"
  val scalatags      = "com.lihaoyi"                %% "scalatags"        % ScalaTagsVersion
  val osLib          = "com.lihaoyi"                %% "os-lib"           % "0.11.4"
  val jsoup          = "org.jsoup"                   % "jsoup"            % "1.21.1"
  val jgit           = "org.eclipse.jgit"            % "org.eclipse.jgit" % "7.3.0.202506031305-r"
  val swaggerParser  = "io.swagger.parser.v3"        % "swagger-parser"   % "2.1.30"
  val playJson       = "org.playframework"          %% "play-json"        % PlayJsonVersion
  val jqueryUi       = "org.webjars"                 % "jquery-ui"        % JQueryUiVersion
  val webjarsPlay    = "org.webjars"                %% "webjars-play"     % "3.0.2"
  val bootstrap      = "org.webjars.npm"             % "bootstrap"        % BootstrapVersion
  val bootstrapTable = "org.webjars.npm"             % "bootstrap-table"  % BootstrapTableVersion
  val bootstrapIcons = "org.webjars.npm"             % "bootstrap-icons"  % BootstrapIconsVersion
  val swaggerUi      = "org.webjars"                 % "swagger-ui"       % "5.25.3"

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
