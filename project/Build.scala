// Based off of https://gist.github.com/trygvis/2266342
// Copy Scalatron/bin/Scalatron.jar to lib/ before compiling

import sbt._
import Keys._

object Build extends Build {
  val botDirectory = SettingKey[File]("bot-directory")
  libraryDependencies ++= Seq("net.databinder.dispatch" %% "dispatch-core" % "0.11.0")
  val start = TaskKey[Unit]("start") <<= (baseDirectory,
    unmanagedClasspath in Compile, javaHome) map {
    (baseDirectory, ucp, java) =>
      ScalatronApiClient.start(baseDirectory,
        ucp.files.filter(_.name.endsWith("Scalatron.jar")).absString, java)
  }

  val stop = TaskKey[Unit]("stop") := ScalatronApiClient.stop()

  val ScalaTron = config("scalatron")

  val deployRemote = TaskKey[Unit]("deploy-remote",
    "Deploys Scalatron bot to a remote server") <<= (baseDirectory, sources in Compile) map { (baseDirectory, sources: Seq[File]) =>
    ScalatronApiClient.deployRemote(baseDirectory, sources)
  }

  val deployLocal = TaskKey[Unit]("deploy-local",
    "Deploys Scalatron bot to local server")   <<= (baseDirectory, packageBin in Compile) map { (baseDirectory, botJar) =>
    ScalatronApiClient.deployLocal(baseDirectory, botJar)
  }

  val deleteBots = TaskKey[Unit]("delete-bots", "Delete all local Scalatron bots") <<= (baseDirectory) map { (baseDirectory) =>
    ScalatronApiClient.deleteBots(baseDirectory)
  }

  val bot = Project(
    id = "mybot",
    base = file("."),
    settings = Project.defaultSettings ++ botSettings ++ inConfig(ScalaTron)(Seq(deployLocal, deployRemote,
      start, stop, deleteBots))).configs(ScalaTron)

  val botSettings = Seq[Setting[_]](
    name := "my-scalatron-bot",
    scalaVersion := "2.9.3",
    unmanagedJars in Compile <++= baseDirectory map { base =>
      val libs = base / "lib"
      val dirs = (libs / "Scalatron" / "bin")
        (dirs ** "*.jar").classpath
    },
    scalacOptions ++= Seq("-deprecation", "-unchecked"))
}
