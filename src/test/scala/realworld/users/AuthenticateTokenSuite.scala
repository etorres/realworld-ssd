package realworld.users

import java.util.UUID

import scala.concurrent.duration.DurationInt

import cats.effect.IO
import cats.effect.testkit.TestControl

import realworld.RequirementSuite
import realworld.users.control.TokenIssuer
import realworld.users.entity.{AuthToken, UserError, UserId}

import UsersFixture.*

/** R5: Authenticate a token. */
class AuthenticateTokenSuite extends RequirementSuite[UsersFixture]:

  protected def setting: IO[UsersFixture] = UsersFixture()

  requirements(
    (
      "R5.1",
      "resolves a token it issued, within its validity window, to the account it was issued for",
      fixture =>
        for
          user <- fixture.stored("jake", "jake@jake.jake")
          token <- fixture.tokens.issue(user.id)
          resolved <- accepted(fixture.users.authenticateToken(token))
        yield assertEquals(resolved, user.id)
    ),
    (
      "R5.2",
      "rejects a token that has expired",
      // Arranges its own fixture: the row is about the passage of time, which TestControl supplies
      // instantly. Storing the account directly keeps bcrypt out of the virtualised runtime.
      _ =>
        TestControl.executeEmbed(
          for
            fixture <- UsersFixture(tokenTtl = 1.hour)
            user <- fixture.stored("jake", "jake@jake.jake")
            token <- fixture.tokens.issue(user.id)
            _ <- IO.sleep(2.hours)
            error <- rejected(fixture.users.authenticateToken(token))
          yield assertEquals(error, UserError.invalidToken)
        )
    ),
    (
      "R5.3",
      "rejects a malformed token, and one whose signature it did not make",
      fixture =>
        // The account exists in both cases, so a rejection can only be about the token itself.
        val foreign = TokenIssuer.hs256[IO]("a-different-secret", TokenTtl)
        for
          user <- fixture.stored("jake", "jake@jake.jake")
          impostor <- foreign.issue(user.id)
          malformed <- rejected(fixture.users.authenticateToken(AuthToken("this.is.not.a.jwt")))
          unsigned <- rejected(fixture.users.authenticateToken(impostor))
        yield
          assertEquals(malformed, UserError.invalidToken)
          assertEquals(unsigned, UserError.invalidToken)
    ),
    (
      "R5.4",
      "rejects a well-formed token naming an account that does not exist",
      fixture =>
        for
          absent <- IO(UserId(UUID.randomUUID()))
          token <- fixture.tokens.issue(absent)
          error <- rejected(fixture.users.authenticateToken(token))
        yield assertEquals(error, UserError.invalidToken)
    )
  )
