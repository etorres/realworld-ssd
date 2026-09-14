package realworld.users

import cats.data.NonEmptyChain
import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Status}

import realworld.RequirementSuite
import realworld.users.boundary.{Credentials, UpdateUser}
import realworld.users.control.FieldUpdate.{Assigned, Cleared, Unchanged}
import realworld.users.entity.*

import UsersFixture.*

/** R4: Update the current account. */
class UpdateUserSuite extends RequirementSuite[UsersFixture]:

  /** Every row starts from one registered account, whose id the check receives alongside the fixture. */
  protected def setting: IO[UsersFixture] =
    UsersFixture().flatTap(_.register("jake", "jake@jake.jake"))

  private def caller(fixture: UsersFixture): IO[UserId] =
    accepted(fixture.users.authenticateUser(Credentials("jake@jake.jake", "password123"))).map(_.user.id)

  /** A command that mentions no field at all; rows `copy` in just the one they are about. */
  private val untouched = UpdateUser(Unchanged, Unchanged, Unchanged, Unchanged, Unchanged)

  private def updating(fixture: UsersFixture, command: UpdateUser) =
    caller(fixture).flatMap(id => accepted(fixture.users.updateUser(id, command)))

  private def refusing(fixture: UsersFixture, command: UpdateUser) =
    caller(fixture).flatMap(id => rejected(fixture.users.updateUser(id, command)))

  requirements(
    (
      "R4.1",
      "applies exactly the submitted fields and leaves every omitted one unchanged",
      fixture =>
        updating(fixture, untouched.copy(bio = Assigned("I work at statefarm"))).map: session =>
          assertEquals(session.user.bio, Some("I work at statefarm"))
          assertEquals(session.user.username.value, "jake")
          assertEquals(session.user.email.value, "jake@jake.jake")
          assertEquals(session.user.image, None)
    ),
    (
      "R4.2",
      "clears a bio or an image submitted as empty",
      fixture =>
        for
          id <- caller(fixture)
          _ <- accepted(
            fixture.users
              .updateUser(id, untouched.copy(bio = Assigned("temporary"), image = Assigned("pic.png")))
          )
          session <- accepted(fixture.users.updateUser(id, untouched.copy(bio = Cleared, image = Cleared)))
        yield
          assertEquals(session.user.bio, None)
          assertEquals(session.user.image, None)
    ),
    (
      "R4.3",
      "replaces the credential so that only the new password authenticates afterwards",
      fixture =>
        for
          _ <- updating(fixture, untouched.copy(password = Assigned("newpassword")))
          stale <- rejected(fixture.users.authenticateUser(Credentials("jake@jake.jake", "password123")))
          fresh <- accepted(fixture.users.authenticateUser(Credentials("jake@jake.jake", "newpassword")))
        yield
          assertEquals(stale, UserError.invalidCredentials)
          assertEquals(fresh.user.username.value, "jake")
    ),
    (
      "R4.4",
      "accepts the caller resubmitting their own current email and username unchanged",
      fixture =>
        updating(
          fixture,
          untouched.copy(email = Assigned("jake@jake.jake"), username = Assigned("jake"))
        ).map: session =>
          assertEquals(session.user.email.value, "jake@jake.jake")
          assertEquals(session.user.username.value, "jake")
    ),
    (
      "R4.5",
      "rejects an email already registered to a different account, as a conflict naming the email",
      fixture =>
        for
          _ <- fixture.register("rival", "rival@jake.jake")
          error <- refusing(fixture, untouched.copy(email = Assigned("rival@jake.jake")))
        yield assertEquals(error, UserError.Conflict(NonEmptyChain.one(FieldError.taken("email"))))
    ),
    (
      "R4.6",
      "rejects a username already taken by a different account, as a conflict naming the username",
      fixture =>
        for
          _ <- fixture.register("rival", "rival@jake.jake")
          error <- refusing(fixture, untouched.copy(username = Assigned("rival")))
        yield assertEquals(error, UserError.Conflict(NonEmptyChain.one(FieldError.taken("username"))))
    ),
    (
      "R4.7",
      "rejects an empty email, username, or password, naming every empty field",
      fixture =>
        refusing(fixture, UpdateUser(Cleared, Cleared, Cleared, Unchanged, Unchanged)).map: error =>
          assertEquals(
            error,
            UserError.Invalid(
              NonEmptyChain(
                FieldError.blank("email"),
                FieldError.blank("username"),
                FieldError.blank("password")
              )
            )
          )
    ),
    (
      "R4.8",
      "rejects a password shorter than 8 characters",
      fixture =>
        refusing(fixture, untouched.copy(password = Assigned("short7c"))).map: error =>
          assertEquals(
            error,
            UserError.Invalid(NonEmptyChain.one(FieldError.tooShort("password", Password.MinimumLength)))
          )
    ),
    (
      "R4.9",
      "rejects a request carrying no authentication as unauthorized, reporting the token as missing",
      fixture =>
        for
          response <- fixture.call(Method.PUT, "/api/user", """{"user":{"bio":"anything"}}""".some)
          body <- response.as[Json]
        yield
          assertEquals(response.status, Status.Unauthorized)
          assertEquals(
            body.hcursor.downField("errors").downField("token").as[List[String]],
            Right(List("is missing"))
          )
    )
  )
