package realworld.profiles

import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Status}

import realworld.RequirementSuite
import realworld.profiles.entity.ProfileError

import ProfilesFixture.*

/** R2: Follow a user. */
class FollowUserSuite extends RequirementSuite[ProfilesFixture]:

  protected def setting: IO[ProfilesFixture] = ProfilesFixture()

  /** A follower and someone worth following. */
  private def pair(fixture: ProfilesFixture) =
    for
      _ <- fixture.register("celeb", "celeb@jake.jake")
      follower <- fixture.register("jake", "jake@jake.jake")
    yield follower.user.id

  requirements(
    (
      "R2.1",
      "records the relationship and returns the profile with the follow flag set",
      fixture =>
        for
          follower <- pair(fixture)
          followed <- accepted(fixture.profiles.followUser(follower, "celeb"))
          seen <- accepted(fixture.profiles.viewProfile(follower.some, "celeb"))
        yield
          assertEquals(followed.following, true)
          assertEquals(seen.following, true, "the relationship did not outlive the request")
    ),
    (
      "R2.2",
      "leaves the relationship unchanged when the caller already follows that account",
      fixture =>
        for
          follower <- pair(fixture)
          _ <- accepted(fixture.profiles.followUser(follower, "celeb"))
          again <- accepted(fixture.profiles.followUser(follower, "celeb"))
          // One unfollow must be enough to undo two follows, which it is only if the second recorded nothing.
          undone <- accepted(fixture.profiles.unfollowUser(follower, "celeb"))
        yield
          assertEquals(again.following, true)
          assertEquals(undone.following, false)
    ),
    (
      "R2.3",
      "rejects a caller naming themselves, as a validation failure",
      fixture =>
        for
          _ <- pair(fixture)
          jake <- accepted(fixture.profiles.viewProfile(caller = None, "jake"))
          follower <- fixture.users.describeUser("jake").map(_.map(_.id).getOrElse(fail("jake vanished")))
          error <- rejected(fixture.profiles.followUser(follower, jake.username))
        yield assertEquals(error, ProfileError.CannotFollowSelf)
    ),
    (
      "R2.4",
      "rejects an unregistered username as not found",
      fixture =>
        for
          follower <- pair(fixture)
          error <- rejected(fixture.profiles.followUser(follower, "nobody"))
        yield assertEquals(error, ProfileError.NoSuchProfile)
    ),
    (
      "R2.5",
      "rejects a request carrying no authentication as unauthorized, before asking whether the account exists",
      fixture =>
        for
          _ <- pair(fixture)
          response <- fixture.call(Method.POST, "/api/profiles/nobody/follow")
          body <- response.as[Json]
        yield
          assertEquals(response.status, Status.Unauthorized)
          assertEquals(
            body.hcursor.downField("errors").downField("token").as[List[String]],
            Right(List("is missing"))
          )
    )
  )
