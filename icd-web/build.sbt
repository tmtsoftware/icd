name := """icd-web"""

val Version = "0.1-SNAPSHOT"

version := Version

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

val `icd-db` = "org.tmt" %% "icd-db" % Version

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws,
  `icd-db`
)
