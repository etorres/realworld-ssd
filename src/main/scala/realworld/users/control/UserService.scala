package realworld.users.control

import cats.Monad
import cats.data.{NonEmptyChain, ValidatedNec}
import cats.effect.std.UUIDGen
import cats.mtl.Raise
import cats.mtl.syntax.all.*
import cats.syntax.all.*

import realworld.users.entity.*

/** An authenticated view of an account: the account itself plus a token that proves it. */
final case class Session(user: User, token: AuthToken)

/** The `users` use cases.
  *
  * Every operation that can be rejected asks for `Raise[F, UserError]`, so the error channel stays visible
  * in the signature (decision D2). Nothing here knows about HTTP.
  */
final class UserService[F[_]: Monad: UUIDGen](
    repository: UserRepository[F],
    hasher: PasswordHasher[F],
    tokens: TokenIssuer[F]
):

  def register(username: String, email: String, password: String)(using
      Raise[F, UserError]
  ): F[Session] =
    for
      fields <- validated((Username.parse(username), Email.parse(email), Password.parse(password)).tupled)
      (name, address, secret) = fields
      _ <- ensureAvailable(address, name, excluding = None)
      id <- UUIDGen[F].randomUUID.map(UserId.apply)
      credential <- hasher.hash(secret)
      user = User(id, name, address, bio = None, image = None, credential = credential)
      _ <- repository.save(user)
      session <- issue(user)
    yield session

  def authenticate(email: String, password: String)(using Raise[F, UserError]): F[Session] =
    for
      // Only blankness is checked here. A malformed address and a well-formed one nobody registered are
      // both simply failed lookups (R2.2), not validation failures.
      fields <- validated((nonBlank("email", email), nonBlank("password", password)).tupled)
      (address, secret) = fields
      found <- repository.findByEmail(Email.lookup(address))
      user <- found.fold(UserError.invalidCredentials.raise[F, User])(_.pure[F])
      matches <- hasher.verify(Password.of(secret), user.credential)
      _ <- UserError.invalidCredentials.raise[F, Unit].unlessA(matches)
      session <- issue(user)
    yield session

  def current(caller: UserId)(using Raise[F, UserError]): F[Session] =
    account(caller).flatMap(issue)

  def update(
      caller: UserId,
      email: FieldUpdate[String],
      username: FieldUpdate[String],
      password: FieldUpdate[String],
      bio: FieldUpdate[String],
      image: FieldUpdate[String]
  )(using Raise[F, UserError]): F[Session] =
    for
      existing <- account(caller)
      fields <- validated(
        (
          mandatory("email", email)(Email.parse),
          mandatory("username", username)(Username.parse),
          mandatory("password", password)(Password.parse)
        ).tupled
      )
      (address, name, secret) = fields
      nextEmail = address.getOrElse(existing.email)
      nextUsername = name.getOrElse(existing.username)
      _ <- ensureAvailable(nextEmail, nextUsername, excluding = caller.some)
      credential <- secret.traverse(hasher.hash)
      updated = existing.copy(
        email = nextEmail,
        username = nextUsername,
        bio = nullable(bio, existing.bio),
        image = nullable(image, existing.image),
        credential = credential.getOrElse(existing.credential)
      )
      _ <- repository.save(updated)
      session <- issue(updated)
    yield session

  /** Absence is an answer, not a rejection (R6.2), so this asks for no `Raise`. */
  def describe(username: String): F[Option[PublicAccount]] =
    repository.findByUsername(Username.lookup(username)).map(_.map(publicly))

  /** The same report as `describe`, reached by identity instead of by name (R6.4). */
  def describeAccount(id: UserId): F[Option[PublicAccount]] = repository.findById(id).map(_.map(publicly))

  def identify(token: AuthToken)(using Raise[F, UserError]): F[UserId] =
    for
      subject <- tokens.subjectOf(token)
      id <- subject.fold(UserError.invalidToken.raise[F, UserId])(_.pure[F])
      // A token can outlive the account it names (R5.4).
      _ <- account(id)
    yield id

  private def publicly(user: User): PublicAccount =
    PublicAccount(user.id, user.username.value, user.bio, user.image)

  private def issue(user: User): F[Session] = tokens.issue(user.id).map(Session(user, _))

  private def account(id: UserId)(using Raise[F, UserError]): F[User] =
    repository.findById(id).flatMap(_.fold(UserError.invalidToken.raise[F, User])(_.pure[F]))

  /** Rejects when the email or the username already belongs to somebody else, naming every clash at once.
    * `excluding` is the caller's own account, so resubmitting one's current values is accepted (R4.4).
    */
  private def ensureAvailable(email: Email, username: Username, excluding: Option[UserId])(using
      Raise[F, UserError]
  ): F[Unit] =
    def takenByAnother(owner: Option[User]): Boolean = owner.exists(user => !excluding.contains(user.id))

    for
      byEmail <- repository.findByEmail(email)
      byUsername <- repository.findByUsername(username)
      clashes = List(
        Option.when(takenByAnother(byEmail))(FieldError.taken("email")),
        Option.when(takenByAnother(byUsername))(FieldError.taken("username"))
      ).flatten
      _ <- NonEmptyChain.fromSeq(clashes).traverse_(UserError.Conflict(_).raise[F, Unit])
    yield ()

  /** A field with no cleared state: left out keeps the current value, mentioned as empty is a rejection. */
  private def mandatory[A](field: String, update: FieldUpdate[String])(
      parse: String => ValidatedNec[FieldError, A]
  ): ValidatedNec[FieldError, Option[A]] =
    update match
      case FieldUpdate.Unchanged => None.validNec
      case FieldUpdate.Cleared => FieldError.blank(field).invalidNec
      case FieldUpdate.Assigned(value) => parse(value).map(_.some)

  /** A field that may be cleared: left out keeps the current value, mentioned as empty erases it. */
  private def nullable(update: FieldUpdate[String], current: Option[String]): Option[String] =
    update match
      case FieldUpdate.Unchanged => current
      case FieldUpdate.Cleared => None
      case FieldUpdate.Assigned(value) => value.some

  private def validated[A](fields: ValidatedNec[FieldError, A])(using Raise[F, UserError]): F[A] =
    fields.fold(errors => UserError.Invalid(errors).raise[F, A], _.pure[F])

  private def nonBlank(field: String, raw: String): ValidatedNec[FieldError, String] =
    if raw.trim.isEmpty then FieldError.blank(field).invalidNec else raw.validNec
