import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.packager.Keys._
import sbt.Keys._
import sbt._

val Version = "0.1-SNAPSHOT"
val ScalaVersion = "2.11.6"

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
  scalacOptions ++= Seq("-target:jvm-1.6", "-encoding", "UTF-8", "-feature", "-deprecation", "-unchecked"),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.6", "-Xlint:unchecked", "-Xlint:deprecation")
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
val xmlworker = "com.itextpdf.tool" % "xmlworker" % "5.5.5"
val casbah = "org.mongodb" %% "casbah" % "2.8.0"
val `slf4j-nop` = "org.slf4j" % "slf4j-nop" % "1.7.10"
val diffson = "org.gnieh" %% "diffson" % "0.3"

lazy val root = (project in file(".")).
  aggregate(icd, `icd-db`)

lazy val icd = project
  .settings(packageSettings("ICD support", "Used to validate ICDs"): _*)
  .settings(libraryDependencies ++=
  compile(jsonSchemaValidator, scopt, typesafeConfig, ficus, pegdown, xmlworker, `slf4j-nop`, diffson) ++
    test(scalaTest)
  )

lazy val `icd-db` = project
  .settings(packageSettings("ICD database support", "Used to access ICD database"): _*)
  .settings(libraryDependencies ++=
  compile(casbah) ++
    test(scalaTest)
  ) dependsOn icd
