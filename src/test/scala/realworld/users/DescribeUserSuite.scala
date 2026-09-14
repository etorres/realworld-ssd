package realworld.users

import cats.effect.IO

import realworld.RequirementSuite
import realworld.users.boundary.UpdateUser
import realworld.users.control.FieldUpdate.{Assigned, Unchanged}
import realworld.users.control.PublicAccount
import realworld.users.entity.UserId

import UsersFixture.*

/** R6: Describe an account. */
class DescribeUserSuite extends RequirementSuite[UsersFixture]:

  protected def setting: IO[UsersFixture] = UsersFixture()

  requirements(
    (
      "R6.1",
      "reports a registered account's identity, username, bio, and image",
      fixture =>
        for
          registered <- fixture.register("jake", "jake@jake.jake")
          _ <- accepted(
            fixture.users.updateUser(
              registered.user.id,
              UpdateUser(
                Unchanged,
                Unchanged,
                Unchanged,
                Assigned("I work at statefarm"),
                Assigned("pic.png")
              )
            )
          )
          described <- fixture.users.describeUser("jake")
        yield assertEquals(
          described,
          Some(PublicAccount(registered.user.id, "jake", Some("I work at statefarm"), Some("pic.png")))
        )
    ),
    (
      "R6.2",
      "reports that no such account exists rather than rejecting the request",
      fixture =>
        for
          _ <- fixture.register("jake", "jake@jake.jake")
          described <- fixture.users.describeUser("nobody")
        yield assertEquals(described, None)
    ),
    (
      "R6.4",
      "reports the same fields for an identity as it reports for that account's username",
      fixture =>
        for
          registered <- fixture.register("jake", "jake@jake.jake")
          _ <- accepted(
            fixture.users.updateUser(
              registered.user.id,
              UpdateUser(Unchanged, Unchanged, Unchanged, Assigned("a bio"), Assigned("pic.png"))
            )
          )
          byName <- fixture.users.describeUser("jake")
          byIdentity <- fixture.users.describeUser(registered.user.id)
          absent <- fixture.users.describeUser(UserId(java.util.UUID.randomUUID()))
        yield
          assertEquals(byIdentity, byName, "the two lookups disagreed")
          assertEquals(byIdentity.map(_.username), Some("jake"))
          assertEquals(absent, None, "an unknown identity was described anyway")
    ),
    (
      "R6.3",
      "never reports the account's email or credential",
      fixture =>
        for
          registered <- fixture.register("jake", "jake@jake.jake", "password123")
          stored <- fixture.repository.findById(registered.user.id)
          hash = stored.getOrElse(fail("the account was not stored")).credential.hash.value
          described <- fixture.users.describeUser("jake")
          account = described.getOrElse(fail("the registered account was not described"))
          // Walks what was actually reported, field by field, so widening `PublicAccount` breaks this row.
          reported = account.productIterator.map(_.toString).mkString(" ")
        yield
          assert(!reported.contains("jake@jake.jake"), s"the description discloses the email: $reported")
          assert(!reported.contains("password123"), s"the description discloses the password: $reported")
          assert(!reported.contains(hash), s"the description discloses the credential: $reported")
    )
  )
