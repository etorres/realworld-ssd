package realworld.users.entity

import java.util.UUID

import scala.util.Try

import cats.data.ValidatedNec
import cats.syntax.all.*

/** A registered account, together with the credential that unlocks it.
  *
  * Only its owner ever reads a `User`; the publicly visible projection belongs to `profiles`.
  */
final case class User(
    id: UserId,
    username: Username,
    email: Email,
    bio: Option[String],
    image: Option[String],
    credential: Credential
)

opaque type UserId = UUID

object UserId:
  def apply(value: UUID): UserId = value

  def parse(raw: String): Option[UserId] = Try(UUID.fromString(raw)).toOption

  extension (id: UserId)
    def value: UUID = id
    def asString: String = id.toString

opaque type Username = String

object Username:
  def parse(raw: String): ValidatedNec[FieldError, Username] =
    val trimmed = raw.trim
    if trimmed.isEmpty then FieldError.blank("username").invalidNec else trimmed.validNec

  /** The lookup form of a name: trimmed but not validated, so an unusable name is simply a failed lookup
    * (R6.2) rather than a rejection.
    */
  def lookup(raw: String): Username = raw.trim

  extension (username: Username) def value: String = username

opaque type Email = String

object Email:
  private val Shape = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$".r

  def parse(raw: String): ValidatedNec[FieldError, Email] =
    val trimmed = raw.trim
    if trimmed.isEmpty then FieldError.blank("email").invalidNec
    else if !Shape.matches(trimmed) then FieldError.invalid("email").invalidNec
    else trimmed.validNec

  /** The lookup form of an address: trimmed but not shape-checked, so authenticating with a malformed
    * address is a failed lookup (R2.2) rather than a validation failure.
    */
  def lookup(raw: String): Email = raw.trim

  extension (email: Email) def value: String = email

/** A plaintext password. It exists only long enough to be hashed or verified, and R1.6 forbids retaining
  * or disclosing it in any other form.
  */
opaque type Password = String

object Password:
  /** NIST SP 800-63B §5.1.1.2: at least 8 characters, with no upper bound below 64 (R1.6, R1.7). */
  val MinimumLength = 8

  def parse(raw: String): ValidatedNec[FieldError, Password] =
    if raw.trim.isEmpty then FieldError.blank("password").invalidNec
    else if raw.length < MinimumLength then FieldError.tooShort("password", MinimumLength).invalidNec
    else raw.validNec

  /** The candidate form of a password: taken as offered, to be checked against a stored credential. */
  def of(raw: String): Password = raw

  extension (password: Password) def value: String = password
