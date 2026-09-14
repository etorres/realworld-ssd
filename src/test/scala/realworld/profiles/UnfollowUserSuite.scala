package realworld.profiles

import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Status}

import realworld.RequirementSuite
import realworld.profiles.entity.ProfileError

import ProfilesFixture.*

/** R3: Unfollow a user. */
class UnfollowUserSuite extends RequirementSuite[ProfilesFixture]:

  protected def setting: IO[ProfilesFixture] = ProfilesFixture()

  private def pair(fixture: ProfilesFixture) =
    for
      _ <- fixture.register("celeb", "celeb@jake.jake")
      follower <- fixture.register("jake", "jake@jake.jake")
    yield follower.user.id

  requirements(
    (
      "R3.1",
      "removes the relationship and returns the profile with the follow flag unset",
      fixture =>
        for
          follower <- pair(fixture)
          _ <- accepted(fixture.profiles.followUser(follower, "celeb"))
          unfollowed <- accepted(fixture.profiles.unfollowUser(follower, "celeb"))
          seen <- accepted(fixture.profiles.viewProfile(follower.some, "celeb"))
        yield
          assertEquals(unfollowed.following, false)
          assertEquals(seen.following, false, "the relationship survived the unfollow")
    ),
    (
      "R3.2",
      "leaves the relationships unchanged when the caller does not follow that account",
      fixture =>
        for
          follower <- pair(fixture)
          other <- fixture.register("other", "other@jake.jake")
          _ <- accepted(fixture.profiles.followUser(other.user.id, "celeb"))
          unfollowed <- accepted(fixture.profiles.unfollowUser(follower, "celeb"))
          // Somebody else's follow of the same account must be untouched.
          theirs <- accepted(fixture.profiles.viewProfile(other.user.id.some, "celeb"))
        yield
          assertEquals(unfollowed.following, false)
          assertEquals(theirs.following, true, "unfollowing removed a relationship belonging to someone else")
    ),
    (
      "R3.3",
      "rejects an unregistered username as not found",
      fixture =>
        for
          follower <- pair(fixture)
          error <- rejected(fixture.profiles.unfollowUser(follower, "nobody"))
        yield assertEquals(error, ProfileError.NoSuchProfile)
    ),
    (
      "R3.4",
      "rejects a request carrying no authentication as unauthorized, before asking whether the account exists",
      fixture =>
        for
          _ <- pair(fixture)
          response <- fixture.call(Method.DELETE, "/api/profiles/nobody/follow")
          body <- response.as[Json]
        yield
          assertEquals(response.status, Status.Unauthorized)
          assertEquals(
            body.hcursor.downField("errors").downField("token").as[List[String]],
            Right(List("is missing"))
          )
    )
  )
