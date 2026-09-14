package realworld.users.entity

import cats.data.NonEmptyChain

/** A rejection this BC can raise. `boundary` translates each case into an HTTP status per system invariant
  * S2; `control` never mentions a status.
  */
enum UserError:
  /** The request was understood but its fields are unusable. */
  case Invalid(errors: NonEmptyChain[FieldError])

  /** A submitted value already belongs to somebody else. */
  case Conflict(errors: NonEmptyChain[FieldError])

  /** The caller is not who they claim to be, or did not say. */
  case Unauthorized(errors: NonEmptyChain[FieldError])

  /** What went wrong, whichever kind of rejection this is. */
  def fieldErrors: NonEmptyChain[FieldError] = this match
    case Invalid(errors) => errors
    case Conflict(errors) => errors
    case Unauthorized(errors) => errors

object UserError:
  def invalid(first: FieldError, rest: FieldError*): UserError = Invalid(NonEmptyChain(first, rest*))

  /** R2.2 and R2.3 share one message, so a rejection never reveals whether an address is registered. */
  val invalidCredentials: UserError = Unauthorized(NonEmptyChain.one(FieldError("credentials", "invalid")))

  /** R3.2, R4.9 */
  val missingToken: UserError = Unauthorized(NonEmptyChain.one(FieldError("token", "is missing")))

  /** R5.2, R5.3, R5.4 */
  val invalidToken: UserError = Unauthorized(NonEmptyChain.one(FieldError("token", "is invalid")))

/** One rejected field and what is wrong with it. The wording follows the RealWorld conformance suite. */
final case class FieldError(field: String, message: String)

object FieldError:
  def blank(field: String): FieldError = FieldError(field, "can't be blank")
  def invalid(field: String): FieldError = FieldError(field, "is invalid")
  def taken(field: String): FieldError = FieldError(field, "has already been taken")

  def tooShort(field: String, minimum: Int): FieldError =
    FieldError(field, s"is too short (minimum is $minimum characters)")
