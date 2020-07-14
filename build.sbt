import sbt._
import Dependencies._
import Settings._
import sbtcrossproject.{crossProject, CrossType}

def compileScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
def testScope(deps: ModuleID*): Seq[ModuleID]    = deps map (_ % "test")

lazy val clients = Seq(icdWebClient)

// Root of the multi-project build
lazy val root = (project in file("."))
  .aggregate(`icd-db`, `icd-git`, icdWebServer)
  .settings(name := "ICD")

// Adds MongoDB database support, ICD versioning, queries, icd-db command line tool
lazy val `icd-db` = project
  .enablePlugins(DeployApp)
  .settings(defaultSettings: _*)
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
        jsoup
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
  ) dependsOn (`icd-db`)

// Adds support for working with ICD model file repositories on GitHub, ICD version management, icd-github tool
lazy val `icd-git` = project
  .enablePlugins(DeployApp)
  .settings(defaultSettings: _*)
  .settings(
    libraryDependencies ++=
      compileScope(jgit) ++
        testScope(scalaTest)
  ) dependsOn (`icd-db`)

// -- Play/ScalaJS parts below --

// a Play framework based web server that goes between icd-db and the web client
lazy val icdWebServer = (project in file("icd-web-server"))
  .settings(defaultSettings: _*)
  .settings(dockerSettings: _*)
  .settings(
    isDevMode in scalaJSPipeline := sys.env.get("SCALAJS_PROD").isEmpty,
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    scalaJSProjects := clients,
    pipelineStages in Assets := Seq(scalaJSPipeline),
    pipelineStages := Seq(digest, gzip),
    // triggers scalaJSPipeline when using compile or continuous compilation
    compile in Compile := ((compile in Compile) dependsOn scalaJSPipeline).value,
    includeFilter in (Assets, LessKeys.less) := "icd.less",
    libraryDependencies ++=
      compileScope(filters, guice, scalajsScripts, playJson, jqueryUi, webjarsPlay, bootstrap, bootstrapTable) ++
        testScope(specs2)
  )
  .enablePlugins(PlayScala, SbtWeb, DockerPlugin)
  .dependsOn(`icd-db`, `icd-git`)

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
  .settings(commonSettings)
  .settings(
    scalaJSUseMainModuleInitializer := false,
    unmanagedSourceDirectories in Compile := Seq((scalaSource in Compile).value),
    skip in packageJSDependencies := false,
    jsDependencies ++= clientJsDeps.value,
    libraryDependencies ++= clientDeps.value,
    Global / onChangedBuildSource := ReloadOnSourceChanges
  )
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb)
  .dependsOn(icdWebSharedJs)

// contains simple case classes used for data transfer that are shared between the client and server
lazy val icdWebShared = (crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure) in file("icd-web-shared"))
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

// loads the server project at sbt startup
onLoad in Global := (onLoad in Global).value andThen { s: State =>
  "project icdWebServer" :: s
}
