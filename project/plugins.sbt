
// Sbt plugins
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.1")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.15.0")
addSbtPlugin("com.vmunier" % "sbt-web-scalajs" % "1.3.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.4")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.4")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
addSbtPlugin("org.scala-js" % "sbt-jsdependencies" % "1.0.2")

resolvers += "jitpack" at "https://jitpack.io"

libraryDependencies += "com.github.tmtsoftware" % "sbt-docs" % "58a91e5"
