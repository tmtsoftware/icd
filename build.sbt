import sbt._
import Dependencies._
import Settings._
//import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
//import org.scalajs.jsdependencies.sbtplugin.JSDependenciesPlugin.autoImport._

def compileScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
def testScope(deps: ModuleID*): Seq[ModuleID]    = deps map (_ % "test")

// Root of the multi-project build
lazy val root = (project in file("."))
  .aggregate(icdWebSharedJvm, `icd-db`, `icd-git`, `icd-viz`, icdWebServer)
  .settings(name := "ICD")

// Adds MongoDB database support, ICD versioning, queries, icd-db command line tool
lazy val `icd-db` = project
  .enablePlugins(DeployApp)
  .settings(defaultSettings: _*)
  .settings(
    ThisBuild / scalafixDependencies ++= Seq(
      "org.reactivemongo" %% "reactivemongo-scalafix" % "1.0.4")
  )
  .settings(
    libraryDependencies ++=
      compileScope(
        akkaSlf4j,
        akkaActorTyped,
        akkaActor,
        akkaStream,
        logbackClassic,
        reactivemongo,
        play2Reactivemongo,
        reactivemongoPlayJsonCompat,
        scalaCsv,
        playJson,
        jsonSchemaValidator,
        scopt,
        scalatags,
        typesafeConfig,
        ficus,
        flexmarkAll,
        itext7Core,
        html2pdf,
        jlatexmath,
        plantuml,
        commonsIo,
        diffson,
        scalaLogging,
        logbackClassic,
        jsoup,
        swaggerParser
      ) ++
        testScope(scalaTest)
  ) dependsOn icdWebSharedJvm

// Command line tool to support visualization of API and ICD relationships
lazy val `icd-viz` = project
  .enablePlugins(DeployApp)
  .settings(defaultSettings: _*)
  .settings(
    libraryDependencies ++=
      compileScope(graphDot) ++
        testScope(scalaTest)
  ) dependsOn `icd-db`

// Adds support for working with ICD model file repositories on GitHub, ICD version management, icd-github tool
lazy val `icd-git` = project
  .enablePlugins(DeployApp)
  .settings(defaultSettings: _*)
  .settings(
    libraryDependencies ++=
      compileScope(jgit) ++
        testScope(scalaTest)
  ) dependsOn `icd-db`

// -- Play/ScalaJS parts below --

// a Play framework based web server that goes between icd-db and the web client
lazy val icdWebServer = (project in file("icd-web-server"))
  .settings(defaultSettings: _*)
  .settings(dockerSettings: _*)
  .settings(
    scalaJSProjects := Seq(icdWebClient),
    Assets / pipelineStages := Seq(scalaJSPipeline),
//    scalaJSPipeline / isDevMode := !sys.env.contains("SCALAJS_PROD"),
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    pipelineStages := Seq(digest, gzip),
    // triggers scalaJSPipeline when using compile or continuous compilation
    Compile / compile := ((Compile / compile) dependsOn scalaJSPipeline).value,
//    includeFilter in (Assets, LessKeys.less) := "icd.less",
    Assets / LessKeys.less / includeFilter := "icd.less",
    libraryDependencies ++=
      compileScope(filters, guice, scalajsScripts, playJson, jqueryUi, webjarsPlay, bootstrap, bootstrapTable) ++
        testScope(specs2)
  )
  .enablePlugins(PlayScala, SbtWeb, DockerPlugin)
  .dependsOn(`icd-db`, `icd-git`, `icd-viz`)

// ScalaJS client JavaScript dependencies
val clientJsDeps = Def.setting(
  Seq(
    "org.webjars" % "jquery"    % JQueryVersion / "jquery.js" minified "jquery.min.js",
    "org.webjars" % "jquery-ui" % JQueryUiVersion / "jquery-ui.min.js" dependsOn "jquery.js",
    // Note: Updating to bootstrap-4 could be a lot of work...
    "org.webjars"       % "bootstrap"       % BootstrapVersion / "bootstrap.min.js" dependsOn "jquery.js",
    "org.webjars.bower" % "bootstrap-table" % BootstrapTableVersion / "bootstrap-table.min.js",
    ProvidedJS / "resize.js" dependsOn "jquery-ui.min.js"
  )
)

// a Scala.js based web client that talks to the Play server
lazy val icdWebClient = (project in file("icd-web-client"))
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb, JSDependenciesPlugin)
  .settings(commonSettings)
  .settings(
    scalaJSUseMainModuleInitializer := false,
    Compile / unmanagedSourceDirectories := Seq((Compile / scalaSource).value),
    packageJSDependencies / skip := false,
    jsDependencies ++= clientJsDeps.value,
    libraryDependencies ++= clientDeps.value,
    Global / onChangedBuildSource := ReloadOnSourceChanges
  )
  .dependsOn(icdWebSharedJs)

// contains simple case classes used for data transfer that are shared between the client and server
lazy val icdWebShared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("icd-web-shared"))
  .jsConfigure(_.enablePlugins(ScalaJSWeb))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "icd.web.shared"
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %%% "play-json" % PlayJsonVersion,
      "com.lihaoyi"       %%% "scalatags" % ScalaTagsVersion
    )
  )

lazy val icdWebSharedJvm = icdWebShared.jvm
lazy val icdWebSharedJs  = icdWebShared.js

//// loads the server project at sbt startup
//Global / onLoad := (onLoad in Global).value andThen { s: State =>
//  "project icdWebServer" :: s
//}
