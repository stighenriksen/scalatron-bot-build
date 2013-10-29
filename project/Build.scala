import sbt._
import Keys._

object Build extends Build {
  val botDirectory = SettingKey[File]("bot-directory")
  libraryDependencies ++= Seq("net.databinder.dispatch" %% "dispatch-core" % "0.11.0",
Defaults.sbtPluginExtra(
    m = "com.github.mpeltonen" % "sbt-idea" % "1.5.2" , // Plugin module name and version
    sbtV = "0.12.0",    // SBT version
    scalaV = "2.9.2"    // Scala version compiled the plugin
  ))

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

  val redeployLast = TaskKey[Unit]("redeploy-last",
    "Deploys Scalatron bot to local server, re-using the same bot name that was previously used.")   <<= (baseDirectory, packageBin in Compile) map { (baseDirectory, botJar) =>
    ScalatronApiClient.redeployLast(baseDirectory, botJar)
  }

  val deleteBots = TaskKey[Unit]("delete-bots", "Delete all local Scalatron bots") <<= (baseDirectory) map { (baseDirectory) =>
    ScalatronApiClient.deleteBots(baseDirectory)
  }

  val bot = Project(
    id = "mybot",
    base = file("."),
    settings = Project.defaultSettings ++ botSettings ++ inConfig(ScalaTron)(Seq(deployLocal, redeployLast, deployRemote,
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
