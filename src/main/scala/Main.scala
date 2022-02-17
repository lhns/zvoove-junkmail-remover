import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.config.ConfigFactory
import fs2.Stream
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder}
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.{Method, Request, RequestCookie, Uri}
import org.log4s.getLogger

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration._

object Main extends IOApp {
  private val logger = getLogger

  case class Session(cookie: RequestCookie)

  def login(client: Client[IO], baseUri: Uri, username: String, password: String): IO[Session] = {
    case class LoginRequest(Username: String, Password: String, RememberMe: Boolean)

    object LoginRequest {
      implicit val codec: Codec[LoginRequest] = deriveCodec
    }

    client.run {
      import org.http4s.circe.CirceEntityEncoder._
      Request(
        method = Method.POST,
        uri = baseUri.resolve(uri"api/v1/account/login")
      ).withEntity(
        LoginRequest(username, password, RememberMe = false)
      )
    }.use { response =>
      response.cookies.find(_.name.startsWith("Login")).fold[IO[Session]] {
        response.as[String].map(message => throw new RuntimeException(s"login failed (${response.status}): $message"))
      } { cookie =>
        IO(Session(RequestCookie(cookie.name, cookie.content)))
      }
    }
  }

  def getBlacklist(client: Client[IO], baseUri: Uri, session: Session, mandantEmail: String): IO[Seq[String]] = {
    case class Mandant(Benutzername: String, ObjectUuid: String)

    object Mandant {
      implicit val codec: Codec[Mandant] = deriveCodec
    }

    case class BlacklistedEmail(Email: String)

    object BlacklistedEmail {
      implicit val codec: Codec[BlacklistedEmail] = deriveCodec
    }

    case class MandantData(BlacklistEmails: Seq[BlacklistedEmail])

    object MandantData {
      implicit val codec: Codec[MandantData] = deriveCodec
    }

    import org.http4s.circe.CirceEntityDecoder._
    (for {
      mandanten <- Stream.eval(client.expect[Seq[Mandant]] {
        Request[IO](
          method = Method.GET,
          uri = baseUri.resolve(uri"api/v1/MandantMailMonitor/GetAll")
        ).addCookie(session.cookie)
      })
      mandant <- Stream.fromOption[IO](mandanten.find(_.Benutzername == mandantEmail))
      mandantData <- Stream.eval(client.expect[MandantData] {
        Request[IO](
          method = Method.GET,
          uri = baseUri.resolve(uri"api/v1/MandantMailMonitor/GetById").withQueryParam("uuid", mandant.ObjectUuid)
        ).addCookie(session.cookie)
      })
      email <- Stream.iterable(mandantData.BlacklistEmails.map(_.Email))
    } yield
      email)
      .compile
      .toList
  }

  case class Email(Bezeichnung: String, ObjectUuid: String)

  object Email {
    implicit val codec: Codec[Email] = deriveCodec
  }

  def listEmails(client: Client[IO], baseUri: Uri, session: Session): IO[Seq[Email]] = {
    import org.http4s.circe.CirceEntityDecoder._
    client.expect[Seq[Email]] {
      Request[IO](
        method = Method.GET,
        uri = baseUri.resolve(uri"api/v1/Posteingang/GetByEingangsmedium?eingangsmediumSystemName=email")
      ).addCookie(session.cookie)
    }
  }

  def deleteEmail(client: Client[IO], baseUri: Uri, session: Session, email: Email): IO[Unit] = {
    client.expect[Unit] {
      import org.http4s.circe.CirceEntityEncoder._
      Request[IO](
        method = Method.POST,
        uri = baseUri.resolve(uri"api/v1/Posteingang/Delete")
      ).addCookie(session.cookie)
        .withEntity(Seq(email.ObjectUuid))
    }
  }

  case class Config(
                     baseUri: Uri,
                     username: String,
                     password: String,
                     blacklistEmail: String,
                     interval: FiniteDuration,
                     dryRun: Option[Boolean],
                   ) {
    val dryRunOrDefault: Boolean = dryRun.getOrElse(true)
  }

  object Config {

    import io.circe.config.syntax._

    implicit val uriCodec: Codec[Uri] = Codec.from(
      Decoder.decodeString.map(Uri.unsafeFromString),
      Encoder.encodeString.contramap(_.renderString)
    )

    implicit val decoder: Decoder[Config] = deriveDecoder

    def load(configFile: Path): Config = {
      ConfigFactory.parseFile(configFile.toFile).as[Config].toTry.get
    }
  }

  private val logFileDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  override def run(args: List[String]): IO[ExitCode] = {
    val configFile = args.lift(0).map(Paths.get(_)).getOrElse(throw new IllegalArgumentException("config file path required"))
    val logFileOption = args.lift(1).map(Paths.get(_))

    logFileOption.foreach(logFile => logger.info(s"Using Logfile: ${logFile.toAbsolutePath}"))

    val config = Config.load(configFile)

    (Stream.emit(()) ++ Stream.awakeDelay[IO](config.interval)).evalTap { _ =>
      logger.debug("Scanning Mails")
      JdkHttpClient.simple[IO].use { client =>
        (for {
          session <- Stream.eval(login(client, config.baseUri, config.username, config.password))
          blacklistPatterns <- Stream.eval(getBlacklist(client, config.baseUri, session, config.blacklistEmail))
          blacklist = blacklistPatterns.map(_.dropWhile(_ == '*'))
          emails <- Stream.eval(listEmails(client, config.baseUri, session))
          filteredEmail <- Stream.iterable(emails).filter(email => blacklist.exists(e => e.length >= 3 && email.Bezeichnung.contains(e)))
          message = s"Deleting ${filteredEmail.Bezeichnung}"
          _ = logger.info(message)
          _ = logFileOption.foreach { logFile =>
            val logFileMessage = s"[${LocalDateTime.now.format(logFileDateFormatter)}] $message\n"
            Files.write(logFile, logFileMessage.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
          }
          _ <- {
            if (config.dryRunOrDefault)
              Stream.empty
            else
              Stream.eval(deleteEmail(client, config.baseUri, session, filteredEmail))
          }
        } yield ())
          .compile.drain
      }.attempt.map(_.swap.map(logger.error(_)("Error")))
    }.compile.drain.as(ExitCode.Success)
  }
}
