package realworld.users.control

import cats.effect.kernel.{Concurrent, Ref, Resource, Sync}
import cats.syntax.all.*
import skunk.codec.all.{text, uuid}
import skunk.implicits.*
import skunk.{Codec, Command, Query, Session, Void}

import realworld.users.entity.*

/** Storage for accounts. Uniqueness is not enforced here: the service checks it so that it can name the
  * offending field (R1.2, R1.3, R4.2, R4.3).
  */
trait UserRepository[F[_]]:
  def findById(id: UserId): F[Option[User]]
  def findByEmail(email: Email): F[Option[User]]
  def findByUsername(username: Username): F[Option[User]]
  def save(user: User): F[Unit]

object UserRepository:

  /** In-memory storage, per decision D3 in the system doc. */
  def inMemory[F[_]: Sync]: F[UserRepository[F]] =
    Ref
      .of[F, Map[UserId, User]](Map.empty)
      .map: store =>
        new UserRepository[F]:
          def findById(id: UserId): F[Option[User]] = store.get.map(_.get(id))

          def findByEmail(email: Email): F[Option[User]] = store.get.map(_.values.find(_.email == email))

          def findByUsername(username: Username): F[Option[User]] =
            store.get.map(_.values.find(_.username == username))

          def save(user: User): F[Unit] = store.update(_.updated(user.id, user))

  /** PostgreSQL storage, per decision D11.
    *
    * `save` is an upsert on the identity, because the in-memory version it replaces was a `Map` keyed by
    * identity and `UserService.update` re-saves an account it already holds.
    */
  def postgres[F[_]: Concurrent](pool: Resource[F, Session[F]]): UserRepository[F] =
    new UserRepository[F]:
      def findById(id: UserId): F[Option[User]] = one(ById, id.value)

      def findByEmail(email: Email): F[Option[User]] = one(ByEmail, email.value)

      def findByUsername(username: Username): F[Option[User]] = one(ByUsername, username.value)

      def save(user: User): F[Unit] =
        pool.use(_.prepare(Upsert).flatMap(_.execute(user))).void

      private def one[A](query: Query[A, User], key: A): F[Option[User]] =
        pool.use(_.prepare(query).flatMap(_.option(key)))

  /** The tables this component owns.
    *
    * The two unique indexes are not what rejects a duplicate — `UserService.ensureAvailable` does that
    * first, so that it can name the field the way R1.2 and R1.3 require. They are here because a table of
    * accounts should not be able to hold two of the same, which is an assurance the `Map` never offered.
    */
  val Tables: List[Command[Void]] =
    List(
      sql"""CREATE TABLE IF NOT EXISTS users (
              id            uuid PRIMARY KEY,
              username      text NOT NULL UNIQUE,
              email         text NOT NULL UNIQUE,
              bio           text,
              image         text,
              password_hash text NOT NULL
            )""".command
    )

  /** Read back with the lookup constructors rather than the parsing ones: a row got here by being
    * validated on the way in, and re-validating it would make a stored account rejectable.
    */
  private val account: Codec[User] =
    (uuid *: text *: text *: text.opt *: text.opt *: text).imap {
      case (id, username, email, bio, image, hash) =>
        User(
          id = UserId(id),
          username = Username.lookup(username),
          email = Email.lookup(email),
          bio = bio,
          image = image,
          credential = Credential(HashedPassword.fromHash(hash))
        )
    }(user =>
      (
        user.id.value,
        user.username.value,
        user.email.value,
        user.bio,
        user.image,
        user.credential.hash.value
      )
    )

  private val Columns = "id, username, email, bio, image, password_hash"

  private val ById: Query[java.util.UUID, User] =
    sql"SELECT #$Columns FROM users WHERE id = $uuid".query(account)

  private val ByEmail: Query[String, User] =
    sql"SELECT #$Columns FROM users WHERE email = $text".query(account)

  private val ByUsername: Query[String, User] =
    sql"SELECT #$Columns FROM users WHERE username = $text".query(account)

  private val Upsert: Command[User] =
    sql"""INSERT INTO users (#$Columns) VALUES ($account)
          ON CONFLICT (id) DO UPDATE SET
            username      = EXCLUDED.username,
            email         = EXCLUDED.email,
            bio           = EXCLUDED.bio,
            image         = EXCLUDED.image,
            password_hash = EXCLUDED.password_hash""".command
