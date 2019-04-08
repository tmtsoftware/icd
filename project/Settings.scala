import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.jse.JsEngineImport.JsEngineKeys
import com.typesafe.sbt.packager.Keys._
import sbt.Keys._
import sbt._

//import scalariform.formatter.preferences._
import com.typesafe.sbt.packager.docker._

// Defines the global build settings so they don't need to be edited everywhere
object Settings {

  val commonSettings = Seq(
    organization := "org.tmt",
    organizationName := "TMT",
    organizationHomepage := Some(url("http://www.tmt.org")),
    version := Dependencies.Version,
    scalaVersion := Dependencies.ScalaVersion)

  // Basic settings
  val buildSettings = commonSettings ++ Seq(
    crossPaths := true,

    // Note: "parallelExecution in Test := false" doesn't seem to prevent parallel execution when all tests are run,
    // which can be a problem in some cases. Besides the fact that all the test output is mixed up,
    // some tests access external resources, such as the location service, redis, hornetq, the config service, etc.,
    // and running these tests in parallel can cause spurious errors (although it would be much faster, if it worked).
    parallelExecution in Test := false,
    // See http://stackoverflow.com/questions/11899723/how-to-turn-off-parallel-execution-of-tests-for-multi-project-builds
    parallelExecution in ThisBuild := false,
    // See https://github.com/sbt/sbt/issues/1886
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
    // Don't buffer test log output (since not parallel)
    logBuffered in Test := false,

    fork := true,
    resolvers += Resolver.typesafeRepo("releases"),
    resolvers += Resolver.sonatypeRepo("releases"),
    resolvers += sbtResolver.value,
    resolvers += "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases",
    resolvers += Resolver.jcenterRepo
  )

  // Using java8
  lazy val defaultSettings = buildSettings ++ Seq(
    scalacOptions ++= Seq("-encoding", "UTF-8", "-feature", "-deprecation", "-unchecked"),
    javacOptions in (Compile, compile) ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    bashScriptExtraDefines ++= Seq(s"addJava -DICD_VERSION=${Dependencies.Version}")
  )

  // Customize the Docker install
  lazy val dockerSettings = Seq(
    maintainer := "TMT Software",
    dockerExposedPorts := Seq(9000),
    dockerBaseImage := "java:8"
  )
}
