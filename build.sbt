import sbt._
import Dependencies._
import Settings._
import org.tmt.sbt.docs.DocKeys._

def providedScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
def compileScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
def testScope(deps: ModuleID*): Seq[ModuleID]    = deps map (_ % "test")
def toPathMapping(f: File): (File, String)       = f          -> f.getName

ThisBuild / docsRepo       := "https://github.com/tmtsoftware/tmtsoftware.github.io.git"
ThisBuild / docsParentDir  := "idbs"
ThisBuild / gitCurrentRepo := "https://github.com/tmtsoftware/icd"

lazy val openSite =
  Def.setting {
    Command.command("openSite") { state =>
      val uri = s"file://${Project.extract(state).get(siteDirectory)}/${docsParentDir.value}/${version.value}/index.html"
      state.log.info(s"Opening browser at $uri ...")
      java.awt.Desktop.getDesktop.browse(new java.net.URI(uri))
      state
    }
  }


// SCALAJS_PROD is set in install.sh to enable fully optimized JavaScript
val optStage = if (sys.env.contains("SCALAJS_PROD")) FullOptStage else FastOptStage

//noinspection ScalaUnusedSymbol
// Root of the multi-project build
lazy val icd = (project in file("."))
  .aggregate(icdWebSharedJvm, `icd-db`, icdWebServer, icdWebSharedJvm, docs)
  .settings(commonSettings)
  .enablePlugins(GithubPublishPlugin)
  .settings(
    commands += openSite.value,
    org.tmt.sbt.docs.Settings.makeSiteMappings(docs)
  )
//  .settings(name := "idbs")

// Adds MongoDB database support, ICD versioning, queries, icd-db command line tool
lazy val `icd-db` = project
  .enablePlugins(DeployApp)
  .settings(defaultSettings)
  .settings(
    libraryDependencies ++=
      compileScope(
        pekkoActorTyped,
        pekkoActor,
        pekkoStream,
        pekkoSerializationJackson,
        jacksonModuleScala,
        reactivemongo,
        reactivemongoPlayJsonCompat,
        scalaCsv,
        playJson,
        jsonSchemaValidator,
        scopt,
        scalatags,
        osLib,
        logbackClassic,
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
        jsoup,
        swaggerParser,
        commonsText,
        graphDot,
        jgit
      ) ++
        testScope(scalaTest)
  ) dependsOn icdWebSharedJvm

// -- Play/ScalaJS parts below --

// a Play framework based web server that goes between icd-db and the web client
lazy val icdWebServer = (project in file("icd-web-server"))
  .settings(defaultSettings)
  .settings(dockerSettings)
  .settings(
    scalaJSProjects := Seq(icdWebClient),
    Assets / pipelineStages := Seq(scalaJSPipeline),
    Global / onChangedBuildSource := ReloadOnSourceChanges,
    pipelineStages := Seq(digest, gzip),
    // triggers scalaJSPipeline when using compile or continuous compilation
    Compile / compile := ((Compile / compile) dependsOn scalaJSPipeline).value,
    libraryDependencies ++=
      compileScope(filters, guice, playJson, jqueryUi, webjarsPlay, bootstrap, bootstrapTable, bootstrapIcons, swaggerUi) ++
        testScope(specs2)
  )
  .enablePlugins(PlayScala, SbtWeb, DockerPlugin)
  .dependsOn(`icd-db`)

// ScalaJS client JavaScript dependencies
val clientJsDeps = Def.setting(
  Seq(
    "org.webjars"     % "jquery"          % JQueryVersion / "jquery.js" minified "jquery.min.js",
    "org.webjars"     % "jquery-ui"       % JQueryUiVersion / "jquery-ui.min.js" dependsOn "jquery.js",
    "org.webjars.npm" % "bootstrap"       % BootstrapVersion / "bootstrap.bundle.js",
    "org.webjars.npm" % "bootstrap-table" % BootstrapTableVersion / "dist/bootstrap-table.min.js",
    ProvidedJS / "resize.js" dependsOn "jquery-ui.min.js"
  )
)

// a Scala.js based web client that talks to the Play server
lazy val icdWebClient = (project in file("icd-web-client"))
  .settings(commonSettings)
  .settings(
    scalaJSUseMainModuleInitializer := false,
    scalaJSStage := optStage,
    Compile / unmanagedSourceDirectories := Seq((Compile / scalaSource).value),
    packageJSDependencies / skip := false,
    jsDependencies ++= clientJsDeps.value,
    libraryDependencies ++= clientDeps.value,
    Compile / fastLinkJS / jsMappings += toPathMapping((Compile / packageJSDependencies).value),
    Compile / fullLinkJS / jsMappings += toPathMapping((Compile / packageMinifiedJSDependencies).value),
    Global / onChangedBuildSource := ReloadOnSourceChanges
  )
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb, JSDependenciesPlugin)
  .dependsOn(icdWebSharedJs)

// contains simple case classes used for data transfer that are shared between the client and server
lazy val icdWebShared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("icd-web-shared"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "icd.web.shared"
  )
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.playframework" %%% "play-json" % PlayJsonVersion,
      "com.lihaoyi"       %%% "scalatags" % ScalaTagsVersion
    )
  )

lazy val icdWebSharedJvm = icdWebShared.jvm
lazy val icdWebSharedJs  = icdWebShared.js

lazy val docs = project
  .settings(commonSettings)
  .enablePlugins(ParadoxMaterialSitePlugin)
