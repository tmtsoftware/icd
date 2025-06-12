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
    scalacOptions ++= Seq("-deprecation", "-encoding", "UTF-8", "-feature", "-unchecked"),
    scalaVersion := Dependencies.ScalaVersion,
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
