package realworld.users

import java.util.UUID

import scala.concurrent.duration.{DurationInt, FiniteDuration}

import cats.effect.IO
import cats.effect.std.{SecureRandom, UUIDGen}
import cats.mtl.Raise
import io.circe.parser
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{HttpApp, Method, Request, Response, Uri}

import realworld.Rejections
import realworld.support.http.Api
import realworld.users.boundary.*
import realworld.users.control.*
import realworld.users.entity.*

/** Everything the `users` requirement suites arrange.
  *
  * The parts are wired by hand rather than through `Users.inMemory`, so a test can look at the stored
  * credential (R1.8) or mint a token for an account that does not exist (R5.4).
  */
final case class UsersFixture(
    users: Users[IO],
    api: HttpApp[IO],
    repository: UserRepository[IO],
    hasher: PasswordHasher[IO],
    tokens: TokenIssuer[IO]
)

object UsersFixture:

  val JwtSecret = "test-secret"
  val TokenTtl: FiniteDuration = 1.hour

  /** bcrypt's cheapest legal cost: these tests hash constantly and do not care how slow an attacker is. */
  val BcryptLogRounds = 4

  def apply(
      jwtSecret: String = JwtSecret,
      tokenTtl: FiniteDuration = TokenTtl
  ): IO[UsersFixture] =
    for
      secureRandom <- SecureRandom.javaSecuritySecureRandom[IO]
      repository <- UserRepository.inMemory[IO]
    yield
      given UUIDGen[IO] = UUIDGen.fromSecureRandom(using cats.Functor[IO], secureRandom)
      val hasher = PasswordHasher.bcrypt[IO](BcryptLogRounds)
      val tokens = TokenIssuer.hs256[IO](jwtSecret, tokenTtl)
      val boundary = Users(UserService(repository, hasher, tokens))
      UsersFixture(
        boundary,
        Api(UsersRoutes(boundary, CallerAuth(boundary)).routes),
        repository,
        hasher,
        tokens
      )

  /** The operation's result, failing the test if it was rejected. */
  def accepted[A](operation: Raise[IO, UserError] ?=> IO[A]): IO[A] =
    Rejections.accepted[UserError, A](operation)

  /** The rejection, failing the test if the operation was accepted. */
  def rejected[A](operation: Raise[IO, UserError] ?=> IO[A]): IO[UserError] =
    Rejections.rejected[UserError, A](operation)

  extension (fixture: UsersFixture)
    /** A registered account, for rows whose subject is something other than registration itself. */
    def register(username: String, email: String, password: String = "password123"): IO[Session] =
      accepted(fixture.users.registerUser(RegisterUser(username, email, password)))

    /** An account written straight to storage, skipping password hashing — for rows whose subject is the
      * token rather than the credential.
      */
    def stored(username: String, email: String): IO[User] =
      IO(UserId(UUID.randomUUID())).flatMap: id =>
        val user = User(
          id = id,
          username =
            Username.parse(username).getOrElse(sys.error(s"the test wrote an invalid username: $username")),
          email = Email.parse(email).getOrElse(sys.error(s"the test wrote an invalid email: $email")),
          bio = None,
          image = None,
          credential = Credential(HashedPassword.fromHash("not-a-real-hash"))
        )
        fixture.repository.save(user).as(user)

    def call(
        method: Method,
        path: String,
        body: Option[String] = None,
        token: Option[AuthToken] = None
    ): IO[Response[IO]] =
      val base = Request[IO](method, Uri.unsafeFromString(path))
      val authorized = token.fold(base)(value => base.putHeaders("Authorization" -> s"Token ${value.value}"))
      fixture.api.run(body.fold(authorized)(raw => authorized.withEntity(json(raw))))

  private def json(raw: String) = parser.parse(raw).getOrElse(sys.error(s"the test wrote invalid JSON: $raw"))
