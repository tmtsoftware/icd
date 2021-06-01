import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.jse.JsEngineImport.JsEngineKeys
import com.typesafe.sbt.packager.Keys._
import sbt.Keys._
import sbt._

//import scalariform.formatter.preferences._
import com.typesafe.sbt.packager.docker._

//noinspection TypeAnnotation
// Defines the global build settings so they don't need to be edited everywhere
object Settings {

  val commonSettings = Seq(
//    organization := "org.tmt",
    organization := "com.github.tmtsoftware.icd",
    organizationName := "TMT",
    organizationHomepage := Some(url("http://www.tmt.org")),
    version := Dependencies.Version,
    scalaVersion := Dependencies.ScalaVersion
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
    scalacOptions ++= Seq("-encoding", "UTF-8", "-feature", "-deprecation", "-unchecked"),
    Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
  )

  // Customize the Docker install
  lazy val dockerSettings = Seq(
    maintainer := "TMT Software",
    dockerExposedPorts := Seq(9000),
    dockerBaseImage := "java:11"
  )
}
