package realworld.users

import cats.data.NonEmptyChain
import cats.effect.IO
import io.circe.syntax.EncoderOps

import realworld.RequirementSuite
import realworld.users.boundary.{Credentials, RegisterUser, UserPayload}
import realworld.users.entity.*

import UsersFixture.*

/** R1: Register a user. */
class RegisterUserSuite extends RequirementSuite[UsersFixture]:

  protected def setting: IO[UsersFixture] = UsersFixture()

  requirements(
    (
      "R1.1",
      "creates the account and returns it with a freshly issued token",
      fixture =>
        accepted(fixture.users.registerUser(RegisterUser("jake", "jake@jake.jake", "password123"))).map:
          session =>
            assertEquals(session.user.username.value, "jake")
            assertEquals(session.user.email.value, "jake@jake.jake")
            assert(session.token.value.nonEmpty, "the session carries no token")
    ),
    (
      "R1.2",
      "rejects an email that is already registered, as a conflict naming the email",
      fixture =>
        for
          _ <- fixture.register("jake", "jake@jake.jake")
          error <- rejected(
            fixture.users.registerUser(RegisterUser("other", "jake@jake.jake", "password123"))
          )
        yield assertEquals(error, UserError.Conflict(NonEmptyChain.one(FieldError.taken("email"))))
    ),
    (
      "R1.3",
      "rejects a username that is already taken, as a conflict naming the username",
      fixture =>
        for
          _ <- fixture.register("jake", "jake@jake.jake")
          error <- rejected(
            fixture.users.registerUser(RegisterUser("jake", "other@jake.jake", "password123"))
          )
        yield assertEquals(error, UserError.Conflict(NonEmptyChain.one(FieldError.taken("username"))))
    ),
    (
      "R1.4",
      "rejects blank fields as a validation failure naming every one of them",
      fixture =>
        rejected(fixture.users.registerUser(RegisterUser("", "  ", ""))).map: error =>
          assertEquals(
            error,
            UserError.Invalid(
              NonEmptyChain(
                FieldError.blank("username"),
                FieldError.blank("email"),
                FieldError.blank("password")
              )
            )
          )
    ),
    (
      "R1.5",
      "rejects a malformed email as a validation failure naming the email",
      fixture =>
        rejected(fixture.users.registerUser(RegisterUser("jake", "not-an-address", "password123"))).map:
          error => assertEquals(error, UserError.Invalid(NonEmptyChain.one(FieldError.invalid("email"))))
    ),
    (
      "R1.6",
      "rejects a password shorter than 8 characters",
      fixture =>
        rejected(fixture.users.registerUser(RegisterUser("jake", "jake@jake.jake", "short7c"))).map: error =>
          assertEquals(
            error,
            UserError.Invalid(NonEmptyChain.one(FieldError.tooShort("password", Password.MinimumLength)))
          )
    ),
    (
      "R1.7",
      "accepts a password of 64 characters and authenticates it afterwards",
      fixture =>
        val long = "a" * 64
        for
          _ <- accepted(fixture.users.registerUser(RegisterUser("jake", "jake@jake.jake", long)))
          session <- accepted(fixture.users.authenticateUser(Credentials("jake@jake.jake", long)))
        yield assertEquals(session.user.username.value, "jake")
    ),
    (
      "R1.8",
      "retains the password only as an irreversible hash and discloses neither it nor the hash",
      fixture =>
        val password = "password123"
        for
          session <- fixture.register("jake", "jake@jake.jake", password)
          stored <- fixture.repository.findById(session.user.id)
          credential = stored.getOrElse(fail("the account was not stored")).credential
          verifies <- fixture.hasher.verify(Password.of(password), credential)
          rendered = UserPayload.from(session).asJson.noSpaces
        yield
          assertNotEquals(credential.hash.value, password, "the password was stored in the clear")
          assert(verifies, "the stored hash does not verify the password it was made from")
          assert(!rendered.contains(password), s"the response discloses the password: $rendered")
          assert(!rendered.contains(credential.hash.value), s"the response discloses the hash: $rendered")
    ),
    (
      "R1.9",
      "leaves the new account's bio and image absent",
      fixture =>
        fixture
          .register("jake", "jake@jake.jake")
          .map: session =>
            assertEquals(session.user.bio, None)
            assertEquals(session.user.image, None)
    )
  )
