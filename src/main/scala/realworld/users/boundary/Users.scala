package realworld.users.boundary

import scala.concurrent.duration.FiniteDuration

import cats.effect.kernel.Sync
import cats.effect.std.{SecureRandom, UUIDGen}
import cats.mtl.Raise
import cats.syntax.all.*

import realworld.users.control.*
import realworld.users.entity.{AuthToken, UserError, UserId}

/** The contract of the `users` business component: what callers, transports, and other components may ask
  * of it. Each method realises one `## Boundary` operation of the capability spec.
  */
trait Users[F[_]]:
  def registerUser(command: RegisterUser)(using Raise[F, UserError]): F[Session]
  def authenticateUser(command: Credentials)(using Raise[F, UserError]): F[Session]
  def getCurrentUser(caller: UserId)(using Raise[F, UserError]): F[Session]
  def updateUser(caller: UserId, command: UpdateUser)(using Raise[F, UserError]): F[Session]
  def authenticateToken(token: AuthToken)(using Raise[F, UserError]): F[UserId]

  /** Reports nothing rather than rejecting when no such account exists (R6.2). */
  def describeUser(username: String): F[Option[PublicAccount]]

  /** The same report, reached by identity rather than by name (R6.4). */
  def describeUser(id: UserId): F[Option[PublicAccount]]

final case class RegisterUser(username: String, email: String, password: String)

final case class Credentials(email: String, password: String)

/** A partial update. A field left out is unchanged (R4.1); an empty one clears where it may be cleared
  * (R4.2) and is rejected where it may not (R4.7).
  */
final case class UpdateUser(
    email: FieldUpdate[String],
    username: FieldUpdate[String],
    password: FieldUpdate[String],
    bio: FieldUpdate[String],
    image: FieldUpdate[String]
)

object Users:

  def apply[F[_]](service: UserService[F]): Users[F] = new Users[F]:

    def registerUser(command: RegisterUser)(using Raise[F, UserError]): F[Session] =
      service.register(command.username, command.email, command.password)

    def authenticateUser(command: Credentials)(using Raise[F, UserError]): F[Session] =
      service.authenticate(command.email, command.password)

    def getCurrentUser(caller: UserId)(using Raise[F, UserError]): F[Session] =
      service.current(caller)

    def updateUser(caller: UserId, command: UpdateUser)(using Raise[F, UserError]): F[Session] =
      service.update(caller, command.email, command.username, command.password, command.bio, command.image)

    def authenticateToken(token: AuthToken)(using Raise[F, UserError]): F[UserId] =
      service.identify(token)

    def describeUser(username: String): F[Option[PublicAccount]] = service.describe(username)

    def describeUser(id: UserId): F[Option[PublicAccount]] = service.describeAccount(id)

  /** The whole component, assembled over in-memory storage (decision D3). */
  def inMemory[F[_]: Sync](jwtSecret: String, tokenTtl: FiniteDuration, bcryptLogRounds: Int): F[Users[F]] =
    for
      secureRandom <- SecureRandom.javaSecuritySecureRandom[F]
      repository <- UserRepository.inMemory[F]
    yield
      given UUIDGen[F] = UUIDGen.fromSecureRandom(using Sync[F], secureRandom)
      apply(
        UserService(
          repository,
          PasswordHasher.bcrypt(bcryptLogRounds),
          TokenIssuer.hs256(jwtSecret, tokenTtl)
        )
      )
