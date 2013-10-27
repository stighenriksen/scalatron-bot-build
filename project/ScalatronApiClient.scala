import com.ning.http.client.{Cookie => ningCookie}
import dispatch._
import Defaults._
import java.io.File
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write
import collection.JavaConverters._

object ScalatronApiClient {

  implicit val formats = Serialization.formats(NoTypeHints)

  val localhost = host("localhost", 8080)

  case class HostConfig(host: String, port: Int)
  case class Connection(local: HostConfig, remote: HostConfig)

  var currentProcess: Option[sbt.Process] = None

  var lastLocalUser: Option[String] = None

  case class ScalatronOption(key: String, value: String) {
    override def toString = s"-$key $value"
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
    def apply(c: ningCookie) : Cookie = {
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

  def start(baseDirectory: String, scalatronJar: String, javaHome: Option[File]) = {
    currentProcess match {
      case Some(_) => sys.error("There's already a scalatron process running! (Use 'scalatron:stop' if you want to start a new one)")
      case None => {
        val configFile = baseDirectory + "/config.json"
        val config = Config(configFile)
        val port = config.connection.local.port

        val javaOptions = config.javaOptions mkString " "
        val scalatronOptions = config.scalatronOptions.toString

        val javaCommand = javaHome.getOrElse("java")

        val cmd = s"$javaCommand $javaOptions " +
          s"-jar $scalatronJar -port $port $scalatronOptions"
        println(s"Running scalatron with command: $cmd")

        val process = sbt.Process(cmd)
        currentProcess = Some(process.run())
      }
    }
  }

  def stop() = {
    currentProcess match {
      case Some(process) => process.destroy(); currentProcess = None; println("Scalatron stopped!")
      case None => sys.error("Can't find a running Scalatron process. Try shutting it down manually.")
    }
  }

  private def login(name: String, password: String): Future[Either[Throwable, (User, List[Cookie])]] = {
    retrieveCookies(localhost / "api" / "users" / name / "session", Password(password)).map { f =>
      f.right.map { cookies =>
        (User(name, password), cookies)
      }
    }
  }

  case class ScalatronUser(name: String, resource: String, session: String)
  case class ScalatronUsers(users: Seq[ScalatronUser])

  def deployLocal(sources: Seq[File]) = {
    val files = Files(sources map { s=>
       val code = scala.io.Source.fromFile(s).mkString
       FileInfo(s.getName, code)
    })
    var promptText = "Deploy local bot as user: "
    promptText = lastLocalUser.map(u => promptText + s" (Leave empty to deploy as '$u')").getOrElse(promptText)
    var name = readLine(promptText).trim
    name = if (name.isEmpty) lastLocalUser.getOrElse("unknown") else name
    val password = readLine("Password: ").trim

    handle( for {
      userAndCookies <- login(name, password).right
      update <- putJsonWithAuth(localhost / "api" / "users" / userAndCookies._1.name / "sources", files, userAndCookies._2)
      build <- putJsonWithAuth(localhost / "api" / "users" / userAndCookies._1.name / "sources" / "build", "", userAndCookies._2)
      deploy <- putJsonWithAuth(localhost / "api" / "users" / userAndCookies._1.name / "unpublished" / "publish", "", userAndCookies._2)
    } yield {lastLocalUser = Some(name); deploy})
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


  private def retrieveCookies(req: dispatch.Req, body: AnyRef): Future[Either[Throwable, List[Cookie]]] = {
    val postRequest = Http((req << write(body) <:< Map("Content-Type" -> "application/json")).POST OK (r => r)).either
    postRequest.right.map(r => r.getCookies.asScala.toList.map(Cookie.apply))
  }

}
