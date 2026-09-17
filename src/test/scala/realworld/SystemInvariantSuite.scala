package realworld

import java.time.Instant

import scala.concurrent.duration.DurationInt

import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Response, Status}

import realworld.articles.ArticlesFixture
import realworld.profiles.ProfilesFixture
import realworld.profiles.ProfilesFixture.*
import realworld.support.http.Timestamps
import realworld.users.UsersFixture
import realworld.users.UsersFixture.*
import realworld.users.control.TokenIssuer
import realworld.users.entity.AuthToken

/** The system invariants from the base-package spec: rules no single capability owns. */
class SystemInvariantSuite extends RequirementSuite[UsersFixture]:

  protected def setting: IO[UsersFixture] = UsersFixture()

  requirements(
    (
      "S1",
      "refuses an unusable token anywhere, and a missing one where the operation requires it",
      fixture =>
        val Garbage = AuthToken("this.is.not.a.jwt")
        val foreign = TokenIssuer.hs256[IO]("a-different-secret", TokenTtl)
        for
          user <- fixture.stored("jake", "jake@jake.jake")
          impostor <- foreign.issue(user.id)
          // An operation that requires authentication: all four triggers reject.
          missing <- fixture.call(Method.GET, "/api/user")
          malformed <- fixture.call(Method.GET, "/api/user", token = Garbage.some)
          unsigned <- fixture.call(Method.GET, "/api/user", token = impostor.some)
          // A token issued already past its expiry, rather than one aged under TestControl: virtual
          // time cannot drive a real socket, and `users` reaches PostgreSQL since D11 (hazard H2).
          expired <-
            for
              aged <- UsersFixture(tokenTtl = -1.hour)
              owner <- aged.stored("jake", "jake@jake.jake")
              token <- aged.tokens.issue(owner.id)
              response <- aged.call(Method.GET, "/api/user", token = token.some)
            yield response.status
          // An operation that merely accepts one: absent means anonymous, but unusable still rejects.
          open <- ProfilesFixture()
          _ <- open.register("celeb", "celeb@jake.jake")
          anonymous <- open.call(Method.GET, "/api/profiles/celeb")
          spoiled <- open.call(Method.GET, "/api/profiles/celeb", token = Garbage.some)
        yield
          assertEquals(missing.status, Status.Unauthorized, "a request with no token was not rejected")
          assertEquals(malformed.status, Status.Unauthorized, "a malformed token was not rejected")
          assertEquals(unsigned.status, Status.Unauthorized, "a token signed elsewhere was not rejected")
          assertEquals(expired, Status.Unauthorized, "an expired token was not rejected")
          assertEquals(anonymous.status, Status.Ok, "an absent optional token should mean anonymous")
          assertEquals(spoiled.status, Status.Unauthorized, "an unusable optional token was not rejected")
    ),
    (
      "S2",
      "renders every rejection as a field-keyed error envelope under the status its kind calls for",
      fixture =>
        val register = (username: String, email: String) =>
          s"""{"user":{"username":"$username","email":"$email","password":"password123"}}"""
        for
          _ <- fixture.call(Method.POST, "/api/users", register("jake", "jake@jake.jake").some)
          unauthorized <- fixture.call(Method.GET, "/api/user")
          conflict <- fixture.call(Method.POST, "/api/users", register("other", "jake@jake.jake").some)
          unprocessable <- fixture.call(Method.POST, "/api/users", register("", "").some)
          missing <- fixture.call(Method.GET, "/api/nothing/here")
          _ <- assertEnvelope(unauthorized, Status.Unauthorized, "token")
          _ <- assertEnvelope(conflict, Status.Conflict, "email")
          _ <- assertEnvelope(unprocessable, Status.UnprocessableContent, "username")
          _ <- assertEnvelope(missing, Status.NotFound, "body")
        yield ()
    ),
    (
      "S3",
      "renders every timestamp it reports as an ISO-8601 UTC instant with milliseconds",
      // Arranges its own fixture: the only timestamps the API reports so far belong to articles.
      _ =>
        for
          // An instant landing exactly on a second is the case the platform renders differently.
          _ <- IO(
            assertEquals(
              Timestamps.render(Instant.parse("2026-01-01T00:00:00Z")),
              "2026-01-01T00:00:00.000Z",
              "a whole-second instant lost its fractional part"
            )
          )
          fixture <- ArticlesFixture()
          author <- fixture.register("jake", "jake@jake.jake")
          response <- fixture.call(
            Method.POST,
            "/api/articles",
            """{"article":{"title":"Timestamped","description":"d","body":"b"}}""".some,
            author.token.some
          )
          body <- response.as[Json]
          article = body.hcursor.downField("article")
        yield
          assertEquals(response.status, Status.Created)
          List("createdAt", "updatedAt").foreach: field =>
            val rendered = article.get[String](field)
            assert(
              rendered.exists(IsoInstant.matches),
              s"$field is not an ISO-8601 UTC instant with milliseconds: $rendered"
            )
    )
  )

  /** The shape S3 fixes. Anchored at both ends, unlike the conformance suite's own pattern. */
  private val IsoInstant = """^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$""".r

  /** Asserts the status, and that the body is an `errors` object naming `field` with a non-empty reason. */
  private def assertEnvelope(response: Response[IO], expected: Status, field: String): IO[Unit] =
    response
      .as[Json]
      .map: body =>
        assertEquals(response.status, expected, s"unexpected status for the $field rejection: $body")
        val messages = body.hcursor.downField("errors").downField(field).as[List[String]]
        assertEquals(
          messages.map(_.exists(_.nonEmpty)),
          Right(true),
          s"no message under errors.$field in $body"
        )
