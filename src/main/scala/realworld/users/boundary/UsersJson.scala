package realworld.users.boundary

import hearth.kindlings.circederivation.{KindlingsCodecAsObject, KindlingsDecoder}
import io.circe.{Codec, Decoder, HCursor}

import realworld.users.control.{FieldUpdate, Session}
import realworld.users.entity.*

/** The wire shapes of the `users` boundary, exactly as the RealWorld API defines them. */
final case class UserPayload(
    email: String,
    token: String,
    username: String,
    bio: Option[String],
    image: Option[String]
)

object UserPayload:
  given Codec.AsObject[UserPayload] = KindlingsCodecAsObject.derived

  def from(session: Session): UserPayload =
    UserPayload(
      email = session.user.email.value,
      token = session.token.value,
      username = session.user.username.value,
      bio = session.user.bio,
      image = session.user.image
    )

final case class UserBody(user: UserPayload)

object UserBody:
  given Codec.AsObject[UserBody] = KindlingsCodecAsObject.derived

/** Request fields stay optional so that a missing one becomes "can't be blank" from the domain (R1.4,
  * R2.4) rather than a JSON decoding failure.
  */
final case class RegisterPayload(username: Option[String], email: Option[String], password: Option[String])

final case class RegisterBody(user: RegisterPayload)

object RegisterBody:
  given Decoder[RegisterBody] = KindlingsDecoder.derived

final case class LoginPayload(email: Option[String], password: Option[String])

final case class LoginBody(user: LoginPayload)

object LoginBody:
  given Decoder[LoginBody] = KindlingsDecoder.derived

final case class UpdatePayload(
    email: FieldUpdate[String],
    username: FieldUpdate[String],
    password: FieldUpdate[String],
    bio: FieldUpdate[String],
    image: FieldUpdate[String]
)

object UpdatePayload:
  /** Hand-written rather than derived, because the three states of a partial update are exactly what a
    * derived `Option` decoder collapses: it cannot tell an absent field from an explicit `null`.
    */
  given Decoder[UpdatePayload] = (cursor: HCursor) =>
    for
      email <- field(cursor, "email")
      username <- field(cursor, "username")
      password <- field(cursor, "password")
      bio <- field(cursor, "bio")
      image <- field(cursor, "image")
    yield UpdatePayload(email, username, password, bio, image)

  /** Absent leaves the field alone; `null` and `""` both mean "empty". */
  private def field(cursor: HCursor, name: String): Decoder.Result[FieldUpdate[String]] =
    val at = cursor.downField(name)
    if at.succeeded then
      at.as[Option[String]].map(_.filter(_.nonEmpty).fold(FieldUpdate.Cleared)(FieldUpdate.Assigned(_)))
    else Right(FieldUpdate.Unchanged)

final case class UpdateBody(user: UpdatePayload)

object UpdateBody:
  given Decoder[UpdateBody] = KindlingsDecoder.derived
