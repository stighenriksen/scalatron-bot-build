import sbt._
import Keys._

object Build extends Build {
  val botDirectory = SettingKey[File]("bot-directory")


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

  val redeployLocal = TaskKey[Unit]("redeploy-local",
    "Deploys Scalatron bot to local server, re-using the same bot name that was previously used.")   <<= (baseDirectory, packageBin in Compile) map { (baseDirectory, botJar) =>
    ScalatronApiClient.redeployLocal(baseDirectory, botJar)
  }


  val stressTest = TaskKey[Unit]("stress-test",
    "Stress test")   <<= (baseDirectory, packageBin in Compile) map { (baseDirectory, botJar) =>
    ScalatronApiClient.stressTest(baseDirectory, botJar)
  }


  val deleteBots = TaskKey[Unit]("delete-bots", "Delete all local Scalatron bots") <<= (baseDirectory) map { (baseDirectory) =>
    ScalatronApiClient.deleteBots(baseDirectory)
  }

  val bot = Project(
    id = "mybot",
    base = file("."),
    settings = Project.defaultSettings ++ botSettings ++ inConfig(ScalaTron)(Seq(deployLocal, redeployLocal, deployRemote,
      start, stop, deleteBots, stressTest))).configs(ScalaTron)

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
