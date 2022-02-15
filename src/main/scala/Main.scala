import cats.effect.{IO, IOApp}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.jdkhttpclient.JdkHttpClient
import org.http4s.{Method, Request, RequestCookie, Uri}

object Main extends IOApp.Simple {
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

  val filter: Seq[String] = Seq("indeedemail", "")

  override def run: IO[Unit] = JdkHttpClient.simple[IO].use { client =>
    val baseUrl = uri"https://drey-personal.europersonal.com"
    for {
      session <- login(client, baseUrl, "", "")
      emails <- listEmails(client, baseUrl, session)
      filteredEmails = emails.filter(email => filter.exists(e => e.length >= 3 && email.Bezeichnung.contains(e)))
      _ = println(filteredEmails)
    } yield ()
  }
}
