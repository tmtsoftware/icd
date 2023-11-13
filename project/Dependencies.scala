import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Dependencies {
  val Version         = "3.0.0"
  val ScalaVersion    = "3.3.1"
  val PekkoVersion    = "1.0.1"
  val PlayJsonVersion = "3.0.1"
//  val ScalaTagsVersion      = "0.11.1" // XXX Can't update to 0.12 due to scalacss not being up to date: Force update?
  val ScalaTagsVersion  = "0.12.0"
  val ScalaJsDomVersion = "2.8.0"
//  val ScalaCssVersion       = "1.0.0"
  val ScalaCssVersion       = "1.0.0"
  val BootstrapVersion      = "5.3.2"
  val JQueryVersion         = "3.6.1"
  val JQueryUiVersion       = "1.13.2"
  val BootstrapTableVersion = "1.22.1"
  val BootstrapIconsVersion = "1.11.1"

  val pekkoActorTyped           = "org.apache.pekko" %% "pekko-actor-typed"           % PekkoVersion
  val pekkoActor                = "org.apache.pekko" %% "pekko-actor"                 % PekkoVersion
  val pekkoStream               = "org.apache.pekko" %% "pekko-stream"                % PekkoVersion
  val pekkoSerializationJackson = "org.apache.pekko" %% "pekko-serialization-jackson" % PekkoVersion

  // command line dependencies
  val scopt               = "com.github.scopt"                 %% "scopt"                  % "4.1.0"
  val jsonSchemaValidator = "com.github.everit-org.json-schema" % "org.everit.json.schema" % "1.14.3"
  val ficus               = "com.iheart"                       %% "ficus"                  % "1.5.2"
  val typesafeConfig      = "com.typesafe"                      % "config"                 % "1.4.3"
  val scalaTest           = "org.scalatest"                    %% "scalatest"              % "3.2.17"

  val flexmarkAll = "com.vladsch.flexmark"  % "flexmark-all" % "0.64.8"
  val scalaCsv    = "com.github.tototoshi" %% "scala-csv"    % "1.3.10"

  val sjsonnet = ("com.databricks" %% "sjsonnet" % "0.4.7")
    .cross(CrossVersion.for3Use2_13)
    .exclude("com.lihaoyi", "scalatags_2.13")
    .exclude("com.lihaoyi", "fastparse_2.13")
    .exclude("com.lihaoyi", "os-lib_2.13")
    .exclude("com.lihaoyi", "geny_2.13")
    .exclude("com.lihaoyi", "pprint_2.13")
    .exclude("org.scala-lang.modules", "scala-collection-compat_2.13")
  // XXX Temp until sjsonnet is updated
  val fastparse                 = "com.lihaoyi"            %% "fastparse"               % "3.0.2"
  val osLib                     = "com.lihaoyi"            %% "os-lib"                  % "0.9.2"
  val pprint                    = "com.lihaoyi"            %% "pprint"                  % "0.8.1"
  val `scala-collection-compat` = "org.scala-lang.modules" %% "scala-collection-compat" % "2.11.0"

  val itext7Core  = "com.itextpdf"       % "itext7-core"  % "8.0.2" pomOnly ()
  val html2pdf    = "com.itextpdf"       % "html2pdf"     % "5.0.2"
  val jlatexmath  = "org.scilab.forge"   % "jlatexmath"   % "1.0.7"
  val commonsIo   = "commons-io"         % "commons-io"   % "2.15.0"
  val commonsText = "org.apache.commons" % "commons-text" % "1.11.0"

  val plantuml = "net.sourceforge.plantuml" % "plantuml" % "1.2023.12"
  val graphDot = ("org.scala-graph" %% "graph-dot" % "1.13.3") // XXX TODO update to 2.0.0, breaking changes
    .cross(CrossVersion.for3Use2_13)

  val reactivemongo               = "org.reactivemongo" %% "reactivemongo"                  % "1.1.0-RC11"
  val reactivemongoPlayJsonCompat = "org.reactivemongo" %% "reactivemongo-play-json-compat" % "1.1.0-play29-RC11"
  //  // XXX Need to fix indirect dependency conflict
  val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.15.3"

  val diffson = "org.gnieh" %% "diffson-play-json" % "4.4.0"

  val scalaLogging   = "com.typesafe.scala-logging" %% "scala-logging"    % "3.9.5"
  val scalatags      = "com.lihaoyi"                %% "scalatags"        % ScalaTagsVersion
  val jsoup          = "org.jsoup"                   % "jsoup"            % "1.16.2"
  val jgit           = "org.eclipse.jgit"            % "org.eclipse.jgit" % "6.7.0.202309050840-r"
  val swaggerParser  = "io.swagger.parser.v3"        % "swagger-parser"   % "2.1.18"
  val playJson       = "org.playframework"          %% "play-json"        % PlayJsonVersion
  val jqueryUi       = "org.webjars"                 % "jquery-ui"        % JQueryUiVersion
  val webjarsPlay    = "org.webjars"                %% "webjars-play"     % "3.0.0"
  val bootstrap      = "org.webjars.npm"             % "bootstrap"        % BootstrapVersion
  val bootstrapTable = "org.webjars.npm"             % "bootstrap-table"  % BootstrapTableVersion
  val bootstrapIcons = "org.webjars.npm"             % "bootstrap-icons"  % BootstrapIconsVersion
  val swaggerUi      = "org.webjars"                 % "swagger-ui"       % "5.9.0"

  // ScalaJS web client scala dependencies
  val clientDeps = Def.setting(
    Seq(
      "org.scala-js"      %%% "scalajs-dom" % ScalaJsDomVersion,
      "com.lihaoyi"       %%% "scalatags"   % ScalaTagsVersion,
      "org.playframework" %%% "play-json"   % PlayJsonVersion,
//      "com.github.japgolly.scalacss" %%% "core"                        % ScalaCssVersion,
      ("com.github.japgolly.scalacss" %%% "ext-scalatags" % ScalaCssVersion)
        .cross(CrossVersion.for3Use2_13),
      "org.scala-js" %%% "scala-js-macrotask-executor" % "1.1.0"
    )
  )

}
