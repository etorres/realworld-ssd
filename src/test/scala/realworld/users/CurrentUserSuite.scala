package realworld.users

import cats.effect.IO
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Status}

import realworld.RequirementSuite

import UsersFixture.*

/** R3: Read the current account. */
class CurrentUserSuite extends RequirementSuite[UsersFixture]:

  protected def setting: IO[UsersFixture] = UsersFixture()

  requirements(
    (
      "R3.1",
      "returns the authenticated caller's own account with a token that still resolves",
      fixture =>
        for
          registered <- fixture.register("jake", "jake@jake.jake")
          caller <- accepted(fixture.users.authenticateToken(registered.token))
          session <- accepted(fixture.users.getCurrentUser(caller))
          resolved <- accepted(fixture.users.authenticateToken(session.token))
        yield
          assertEquals(session.user.id, registered.user.id)
          assertEquals(session.user.email.value, "jake@jake.jake")
          assertEquals(resolved, registered.user.id)
    ),
    (
      "R3.2",
      "rejects a request carrying no authentication as unauthorized, reporting the token as missing",
      fixture =>
        for
          response <- fixture.call(Method.GET, "/api/user")
          body <- response.as[Json]
        yield
          assertEquals(response.status, Status.Unauthorized)
          assertEquals(
            body.hcursor.downField("errors").downField("token").as[List[String]],
            Right(List("is missing"))
          )
    )
  )
