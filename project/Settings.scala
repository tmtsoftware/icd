import com.typesafe.sbt.site.SitePlugin.autoImport.siteDirectory
import org.tmt.sbt.docs.DocKeys._
import com.typesafe.sbt.packager.Keys.*
import sbt.Keys.*
import sbt.*

//noinspection TypeAnnotation
// Defines the global build settings so they don't need to be edited everywhere
object Settings {

  val commonSettings = Seq(
    organization := "com.github.tmtsoftware.icd",
    organizationName := "TMT",
    organizationHomepage := Some(url("http://www.tmt.org")),
    version := Dependencies.Version,
    scalacOptions ++= Seq("-deprecation", "-encoding", "UTF-8", "-feature", "-unchecked", "-Xsource:3"),
    scalaVersion := Dependencies.ScalaVersion,
  )

  val docSettings = commonSettings ++ Seq(
    docsRepo       := "https://github.com/tmtsoftware/tmtsoftware.github.io.git",
    docsParentDir  := "idbs",
    gitCurrentRepo := "https://github.com/tmtsoftware/icd",
    commands += Command.command("openSite") { state =>
      val uri = s"file://${Project.extract(state).get(siteDirectory)}/${docsParentDir.value}/${version.value}/index.html"
      state.log.info(s"Opening browser at $uri ...")
      java.awt.Desktop.getDesktop.browse(new java.net.URI(uri))
      state
    },
  )

  // Basic settings
  val buildSettings = commonSettings ++ Seq(
    crossPaths := true,
    Test / parallelExecution := false,
    ThisBuild / parallelExecution := false,
    Global / concurrentRestrictions += Tags.limit(Tags.Test, 1),
    Test / logBuffered := false,
    fork := true,
    resolvers += "jitpack" at "https://jitpack.io",
    ThisBuild / isSnapshot := true
  )

  lazy val defaultSettings = buildSettings ++ Seq(
    Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
  )

  // Customize the Docker install
  lazy val dockerSettings = Seq(
    maintainer := "TMT Software",
    dockerExposedPorts := Seq(9000),
    dockerBaseImage := "java:17"
  )
}
