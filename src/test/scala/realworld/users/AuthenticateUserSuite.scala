package realworld.users

import cats.data.NonEmptyChain
import cats.effect.IO

import realworld.RequirementSuite
import realworld.users.boundary.Credentials
import realworld.users.entity.{FieldError, UserError}

import UsersFixture.*

/** R2: Authenticate a user. */
class AuthenticateUserSuite extends RequirementSuite[UsersFixture]:

  private val Registered = ("jake", "jake@jake.jake", "password123")

  /** Every row starts from one registered account. */
  protected def setting: IO[UsersFixture] =
    UsersFixture().flatTap(_.register(Registered._1, Registered._2, Registered._3))

  requirements(
    (
      "R2.1",
      "returns the account with a freshly issued token for matching credentials",
      fixture =>
        accepted(fixture.users.authenticateUser(Credentials("jake@jake.jake", "password123"))).map: session =>
          assertEquals(session.user.username.value, "jake")
          assert(session.token.value.nonEmpty, "the session carries no token")
    ),
    (
      "R2.2",
      "rejects an unregistered email as unauthorized, reporting the credentials as invalid",
      fixture =>
        rejected(fixture.users.authenticateUser(Credentials("nobody@jake.jake", "password123"))).map: error =>
          assertEquals(error, UserError.invalidCredentials)
    ),
    (
      "R2.3",
      "rejects a wrong password as unauthorized, indistinguishably from an unregistered email",
      fixture =>
        rejected(fixture.users.authenticateUser(Credentials("jake@jake.jake", "wrongpassword"))).map: error =>
          assertEquals(error, UserError.invalidCredentials)
    ),
    (
      "R2.4",
      "rejects blank credentials as a validation failure naming every blank field",
      fixture =>
        rejected(fixture.users.authenticateUser(Credentials("", " "))).map: error =>
          assertEquals(
            error,
            UserError.Invalid(NonEmptyChain(FieldError.blank("email"), FieldError.blank("password")))
          )
    )
  )
