import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.packager.Keys._
import sbt.Keys._
import sbt._

val Version = "0.1-SNAPSHOT"
val ScalaVersion = "2.11.5"

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

val buildSettings = Seq(
  organization := "org.tmt",
  organizationName := "TMT",
  organizationHomepage := Some(url("http://www.tmt.org")),
  version := Version,
  scalaVersion := ScalaVersion,
  crossPaths := true,
  parallelExecution in Test := false,
  fork := true,
  resolvers += Resolver.typesafeRepo("releases"),
  resolvers += Resolver.sonatypeRepo("releases"),
  resolvers += sbtResolver.value
)

lazy val defaultSettings = buildSettings ++ formatSettings ++ Seq(
  scalacOptions ++= Seq("-target:jvm-1.8", "-encoding", "UTF-8", "-feature", "-deprecation", "-unchecked"),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked", "-Xlint:deprecation")
)

// For standalone applications
def packageSettings(summary: String, desc: String) = defaultSettings ++
  packagerSettings ++ packageArchetype.java_application ++ Seq(
  version in Rpm := Version,
  rpmRelease := "0",
  rpmVendor := "TMT Common Software",
  rpmUrl := Some("http://www.tmt.org"),
  rpmLicense := Some("MIT"),
  rpmGroup := Some("CSW"),
  packageSummary := summary,
  packageDescription := desc,
  bashScriptExtraDefines ++= Seq(s"addJava -DCSW_VERSION=$Version")
)

def compile(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
def provided(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
def test(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")

// dependencies
val scopt = "com.github.scopt" %% "scopt" % "3.3.0"
val jsonSchemaValidator = "com.github.fge" % "json-schema-validator" % "2.2.6"
val ficus = "net.ceedubs" %% "ficus" % "1.1.2"
val typesafeConfig = "com.typesafe" % "config" % "1.2.1"
val scalaTest = "org.scalatest" %% "scalatest" % "2.1.5"
val pegdown = "org.pegdown" % "pegdown" % "1.4.2"

lazy val icd = (project in file("."))
  .settings(packageSettings("CSW ICD support", "Used to validate ICDs"): _*)
  .settings(libraryDependencies ++=
  compile(jsonSchemaValidator, scopt, typesafeConfig, ficus, pegdown) ++
    test(scalaTest)
  )
