// Comment to get more information during initialization
logLevel := Level.Warn

// Resolvers
resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// Sbt plugins

addSbtPlugin("com.vmunier" % "sbt-web-scalajs" % "1.0.5")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.5")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.19")

addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.0.0")

