//// Comment to get more information during initialization
//logLevel := Level.Warn
//

//lazy val plugins = (project in file(".")).settings(
//  scalaVersion := "2.12.17", // TODO: remove when upgraded to sbt 1.8.0 (maybe even 1.7.3), see https://github.com/sbt/sbt/pull/7021
//)

// Sbt plugins
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.9.0-M2")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.11.0")
addSbtPlugin("com.vmunier" % "sbt-web-scalajs" % "1.2.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.4")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.11")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.4")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
addSbtPlugin("org.scala-js" % "sbt-jsdependencies" % "1.0.2")
