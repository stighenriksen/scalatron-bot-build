import com.ning.http.client.{Cookie => ningCookie}
import dispatch._
import Defaults._
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write
import collection.JavaConverters._
import sbt._

object ScalatronApiClient {

  implicit val formats = Serialization.formats(NoTypeHints)

  val localhost = host("localhost", 8080)

  case class HostConfig(host: String, port: Int)

  case class Connection(local: HostConfig, remote: HostConfig)

  var currentProcess: Option[sbt.Process] = None

  var lastLocalBot: Option[String] = None
  var lastRemoteUser: Option[String] = None

  var cookieStore: Map[String, Option[List[Cookie]]] = Map()

  case class ScalatronOption(key: String, value: String) {
    override def toString = "-" + key + " " + value
  }

  case class Config(connection: Connection, scalatronOptions: ScalatronOptions, javaOptions: Seq[String])

  case class ScalatronOptions(serverOptions: Seq[ScalatronOption], botwarOptions: Seq[ScalatronOption]) {
    override def toString = serverOptions.map(_.toString).mkString(" ") + " " + botwarOptions.map(_.toString).mkString(" ")
  }

  object Config {
    def apply(filePath: String): Config = {
      val lines = scala.io.Source.fromFile(filePath).mkString
      parse(lines).extract[Config]
    }
  }

  case class User(name: String, password: String)

  case class Password(password: String)

  case class FileInfo(filename: String, code: String)

  case class Files(files: Seq[FileInfo], versionLabel: String = "Unpublished bot")


  case class Cookie(domain: String, name: String, value: String, path: String, maxAge: Int,
                    secure: Boolean, version: Int, comment: String, commentUrl: String, httpOnly: Boolean,
                    discard: Boolean, ports: Set[Integer])

  object Cookie {
    def apply(c: ningCookie): Cookie = {
      new Cookie(c.getDomain, c.getName, c.getValue, c.getPath, c.getMaxAge, c.isSecure, c.getVersion,
        c.getComment, c.getCommentUrl, c.isHttpOnly, c.isDiscard, c.getPorts.asScala.toSet)
    }

    def toNingCookie(c: Cookie) = {
      new ningCookie(c.domain, c.name, c.value, c.path, c.maxAge, c.secure, c.version, c.httpOnly, c.discard, c.comment,
        c.commentUrl, c.ports.toList.asJava)
    }
  }

  private def handle(res: Future[Either[Throwable, String]]) = {
    Await.result(res, Duration(10, TimeUnit.SECONDS)) match {
      case Left(t) => sys.error(t.getMessage)
      case Right(s) => println(s)
    }
  }

  def start(baseDirectory: File, scalatronJar: String, javaHome: Option[File]) = {
    currentProcess match {
      case Some(_) => sys.error("There's already a scalatron process running! (Use 'scalatron:stop' if you want to start a new one)")
      case None => {
        val configFile = baseDirectory + "/config.json"
        val config = Config(configFile)
        val port = config.connection.local.port

        val javaOptions = config.javaOptions mkString " "
        val scalatronOptions = config.scalatronOptions.toString

        val javaCommand = javaHome.getOrElse("java")

        val cmd = javaCommand + " " + javaOptions + " " +
          "-jar " + scalatronJar + " " + "-port " + port + " " + scalatronOptions
        println("Running scalatron with command: " + cmd)

        val process = sbt.Process(cmd)

        val log = baseDirectory / "server.log"
        class Logger extends sbt.ProcessLogger {

          def info(s: => String): Unit = IO.writeLines(log, Seq(s), append = true)

          def error(s: => String): Unit = IO.writeLines(log, Seq(s), append = true)

          def buffer[T](f: => T): T = f
        }
        currentProcess = Some(process.run(new Logger))
        println("Scalatron server is running! Logging to " + log.absolutePath)
        println("Browser UI: http://localhost: " + port)
      }
    }
  }

  def stop() = {
    currentProcess match {
      case Some(process) => process.destroy(); currentProcess = None; println("Scalatron stopped!")
      case None => sys.error("Can't find a running Scalatron process. Try shutting it down manually.")
    }
  }

  private def login(name: String, password: String, host: dispatch.Req): Future[Either[Throwable, (User, List[Cookie])]] = {
    retrieveCookies(host / "api" / "users" / name / "session", Password(password), name).map {
      f =>
        f.right.map {
          cookies =>
            (User(name, password), cookies)
        }
    }
  }

  case class ScalatronUser(name: String, resource: String, session: String)

  case class ScalatronUsers(users: Seq[ScalatronUser])

  // TODO Refactor me!
  def deployRemote(baseDirectory: File, sources: Seq[File]) {
    val files = Files(sources map {
      s =>
        val code = scala.io.Source.fromFile(s).mkString
        FileInfo(s.getName, code)
    })

    val configFile = baseDirectory + "/config.json"
    val config = Config(configFile)
    val remoteHostSetting = Option(config.connection.remote.host).map(_.trim).filterNot(_.isEmpty)
    val remotePortSetting = Option(config.connection.remote.port)

    (remoteHostSetting, remotePortSetting) match {
      case (None, None) => sys.error("Missing remote host and port. Update config.json in the projects base directory.")
      case (Some(remoteHost), None) => sys.error("Missing remote port. Update config.json in the projects base directory.")
      case (None, Some(remotePort)) => sys.error("Missing remote host. Update config.json in the projects base directory.")
      case (Some(remoteHost), Some(remotePort)) => {
        var promptText = "Deploy bot as user: "
        promptText = lastRemoteUser.map(u => promptText + "(Leave empty to deploy as '$u') ").getOrElse(promptText)
        val name = Option(readLine(promptText).trim).filterNot(_.isEmpty).orElse(lastRemoteUser)
        name match {
          case None => {
            println("Username cannot be empty.")
            deployRemote(baseDirectory, sources)
          }
          case Some(user) => {
            {
              val remoteScalatronServer = host(remoteHost, remotePort)
              val password = readLine("Password: ").trim
              println("")
              println("Deploying to " + remoteHost + ":" + remotePort + "...")
              handle(for {
                userAndCookies <- login(user, password, remoteScalatronServer).right
                update <- putJsonWithAuth(remoteScalatronServer / "api" / "users" / userAndCookies._1.name / "sources", files, userAndCookies._2)
                build <- putJsonWithAuth(remoteScalatronServer / "api" / "users" / userAndCookies._1.name / "sources" / "build", "", userAndCookies._2)
                deploy <- putJsonWithAuth(remoteScalatronServer / "api" / "users" / userAndCookies._1.name / "unpublished" / "publish", "", userAndCookies._2)
              } yield {
                deploy.right.foreach {
                  _ => lastRemoteUser = Some(user); println("Remote deploy successful!")
                }; deploy
              })
            }
          }
        }
      }
    }
  }

  def deployLocal(baseDirectory: File, botJar: File) {
    var promptText = "Bot name: "
    promptText = lastLocalBot.map(u => promptText + "(Leave empty to deploy as '" + u +"') ").getOrElse(promptText)

    var botName = readLine(promptText).trim

    botName = (Option(botName).filterNot(_.isEmpty), lastLocalBot) match {
      case (Some(bot), _) => bot
      case (None, Some(bot)) => bot
      case (None, None) => {
        ""
      }
    }

    Option(botName).filterNot(_.isEmpty) match {
      case Some(bot) => {
        println("Deploying '" + botName + "'...")

        val botDir = baseDirectory / "lib" / "Scalatron" / "bots"
        IO createDirectory (botDir / botName)
        IO copyFile(botJar, baseDirectory / "lib" / "Scalatron" / "bots" / botName / "ScalatronBot.jar")
        lastLocalBot = Some(botName)
        println("Done! Refresh the display window with 'r' to see your bot.")
      }
      case None =>   println("Please specify a non-empty bot name."); deployLocal(baseDirectory, botJar)
    }

  }

  def redeployLast(baseDirectory: File, botJar: File) = {

    lastLocalBot match {
      case Some(botName) => {
        println("Deploying '" + botName + "'...")
        val botDir = baseDirectory / "lib" / "Scalatron" / "bots"
        IO createDirectory (botDir / botName)
        IO copyFile(botJar, baseDirectory / "lib" / "Scalatron" / "bots" / botName / "ScalatronBot.jar")

        println("Done! Refresh the display window with 'r' to see your bot.")
      }
      case None => {
        deployLocal(baseDirectory, botJar)
      }
    }
  }

  def deleteBots(baseDirectory: File) = {
    val botDir = baseDirectory / "lib" / "Scalatron" / "bots"

    if (botDir.exists()) {
      IO.delete(botDir.listFiles().filter(_.name != "Reference"))
    }

    println("Bots deleted")
  }

  // Utils //
  private def postJson(req: dispatch.Req, body: AnyRef) = {
    Http((req << write(body) <:< Map("Content-Type" -> "application/json")).POST OK as.String).either
  }

  private def putJson(req: dispatch.Req, body: AnyRef) = {
    Http((req << write(body) <:< Map("Content-Type" -> "application/json")).PUT OK as.String).either
  }

  private def postJsonWithAuth(req: dispatch.Req, body: AnyRef, cookies: List[Cookie]): Future[Either[Throwable, String]] = {
    postJson(cookies.foldLeft(req)((r, c) => r.addCookie(Cookie.toNingCookie(c))), body)
  }

  private def putJsonWithAuth(req: dispatch.Req, body: AnyRef, cookies: List[Cookie]): Future[Either[Throwable, String]] = {
    putJson(cookies.foldLeft(req)((r, c) => r.addCookie(Cookie.toNingCookie(c))), body)
  }

  private def retrieveCookies(req: dispatch.Req, body: AnyRef, username: String): Future[Either[Throwable, List[Cookie]]] = {
    val postRequest = Http((req << write(body) <:< Map("Content-Type" -> "application/json")).POST OK (r => r)).either
    postRequest.right.map(r => {
      val cookies = r.getCookies.asScala.toList.map(Cookie.apply)
      //if (cookies.nonEmpty) {
      //   cookieStore = cookieStore.updated(username, Some(cookies))
      // }
      cookies
    })
  }

}
