import sbt._

import Defaults._

resolvers += Resolver.url("sbt-plugin-releases",
  new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(
    Resolver.ivyStylePatterns)


// Resolved to:
//
//  http://..../com.untyped/sbt-less/scala_2.9.1/sbt_0.11.3/0.4/jars/sbt-less.jar
//
  libraryDependencies ++= Seq("net.databinder.dispatch" %% "dispatch-core" % "0.11.0",
Defaults.sbtPluginExtra(
    m = "com.github.mpeltonen" % "sbt-idea" % "1.5.2" , // Plugin module name and version
    sbtV = "0.12",    // SBT version
    scalaV = "2.9.2"    // Scala version compiled the plugin
  ))
