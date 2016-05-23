import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.jse.JsEngineImport.JsEngineKeys
import com.typesafe.sbt.packager.Keys._
import sbt.Keys._
import sbt._

import scalariform.formatter.preferences._
import com.typesafe.sbt.packager.docker._

// Defines the global build settings so they don't need to be edited everywhere
object Settings {

  // Basic settings
  val buildSettings = Seq(
    organization := "org.tmt",
    organizationName := "TMT",
    organizationHomepage := Some(url("http://www.tmt.org")),
    version := Dependencies.Version,
    scalaVersion := Dependencies.ScalaVersion,
    crossPaths := true,
    parallelExecution in Test := false,
    fork := true,
    resolvers += Resolver.typesafeRepo("releases"),
    resolvers += Resolver.sonatypeRepo("releases"),
    resolvers += sbtResolver.value,
    resolvers += "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases",
    resolvers += Resolver.jcenterRepo
  )

  // Automatic code formatting
  def formattingPreferences: FormattingPreferences =
    FormattingPreferences()
      .setPreference(RewriteArrowSymbols, true)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)

  lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test := formattingPreferences
  )

  // Using java8
  lazy val defaultSettings = buildSettings ++ formatSettings ++ Seq(
    scalacOptions ++= Seq("-target:jvm-1.8", "-encoding", "UTF-8", "-feature", "-deprecation", "-unchecked"),
    javacOptions in Compile ++= Seq("-source", "1.8"),
    javacOptions in (Compile, compile) ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked", "-Xlint:deprecation"),
    bashScriptExtraDefines ++= Seq(s"addJava -DCSW_VERSION=${Dependencies.Version}")
  )

  // Customize the Docker install
  lazy val dockerSettings = Seq(
    maintainer := "TMT Software",
    dockerExposedPorts := Seq(9000),
    dockerBaseImage := "java:8"
  )
}
