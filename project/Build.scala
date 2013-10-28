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
      ScalatronApiClient.start(baseDirectory.toPath.toAbsolutePath.toString,
        ucp.files.filter(_.name.endsWith("Scalatron.jar")).absString, java)
  }

  val stop = TaskKey[Unit]("stop") := ScalatronApiClient.stop()

  val ScalaTron = config("scalatron")

  val deployLocal = TaskKey[Unit]("deploy-local", "Deploys Scalatron bot to local server")
  val deployLocalTask = deployLocal <<= (sources in Compile) map { (sources: Seq[File]) =>
      ScalatronApiClient.deployLocal(sources)
  }

  val bot = Project(
    id = "mybot",
    base = file("."),
    settings = Project.defaultSettings ++ botSettings ++ inConfig(ScalaTron)(Seq(deployLocalTask, start, stop))).configs(ScalaTron)

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
