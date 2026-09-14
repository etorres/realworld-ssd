package realworld.profiles

import cats.effect.IO
import cats.syntax.all.*

import realworld.RequirementSuite
import realworld.profiles.entity.{Profile, ProfileError}
import realworld.users.entity.UserId

import ProfilesFixture.*

/** R1: View a profile. */
class ViewProfileSuite extends RequirementSuite[ProfilesFixture]:

  protected def setting: IO[ProfilesFixture] = ProfilesFixture()

  /** The account every row looks at, with fields that only `users` could have supplied. */
  private def celebrity(fixture: ProfilesFixture) =
    fixture.registerDescribed("celeb", "celeb@jake.jake", "I work at statefarm", "smiley.jpg")

  private val Celebrity = Profile("celeb", Some("I work at statefarm"), Some("smiley.jpg"), following = false)

  requirements(
    (
      "R4.1",
      "returns a known account's profile by identity, with the follow flag view-profile would set",
      fixture =>
        for
          celeb <- celebrity(fixture)
          reader <- fixture.register("jake", "jake@jake.jake")
          before <- fixture.profiles.viewAuthor(celeb.user.id, reader.user.id.some)
          _ <- accepted(fixture.profiles.followUser(reader.user.id, "celeb"))
          after <- fixture.profiles.viewAuthor(celeb.user.id, reader.user.id.some)
          anonymous <- fixture.profiles.viewAuthor(celeb.user.id, caller = None)
          byName <- accepted(fixture.profiles.viewProfile(reader.user.id.some, "celeb"))
        yield
          assertEquals(before, Some(Celebrity))
          assertEquals(after, Some(Celebrity.copy(following = true)))
          assertEquals(anonymous, Some(Celebrity))
          assertEquals(after, Some(byName), "view-author and view-profile disagreed on the follow flag")
    ),
    (
      "R4.2",
      "reports that none exists when no account holds the presented identity",
      fixture =>
        for
          _ <- celebrity(fixture)
          absent <- fixture.profiles.viewAuthor(UserId(java.util.UUID.randomUUID()), caller = None)
        yield assertEquals(absent, None)
    ),
    (
      "R1.1",
      "returns the account's username, bio, and image to an anonymous caller, with the follow flag unset",
      fixture =>
        for
          _ <- celebrity(fixture)
          profile <- accepted(fixture.profiles.viewProfile(caller = None, "celeb"))
        yield assertEquals(profile, Celebrity)
    ),
    (
      "R1.2",
      "sets the follow flag for an authenticated caller who follows that account",
      fixture =>
        for
          _ <- celebrity(fixture)
          reader <- fixture.register("jake", "jake@jake.jake")
          _ <- accepted(fixture.profiles.followUser(reader.user.id, "celeb"))
          profile <- accepted(fixture.profiles.viewProfile(reader.user.id.some, "celeb"))
        yield assertEquals(profile, Celebrity.copy(following = true))
    ),
    (
      "R1.3",
      "leaves the follow flag unset for an authenticated caller who does not follow that account",
      fixture =>
        for
          _ <- celebrity(fixture)
          reader <- fixture.register("jake", "jake@jake.jake")
          profile <- accepted(fixture.profiles.viewProfile(reader.user.id.some, "celeb"))
        yield assertEquals(profile, Celebrity)
    ),
    (
      "R1.4",
      "leaves the follow flag unset when a caller views their own profile",
      fixture =>
        for
          own <- fixture.registerDescribed("jake", "jake@jake.jake", "my bio", "me.jpg")
          profile <- accepted(fixture.profiles.viewProfile(own.user.id.some, "jake"))
        yield assertEquals(profile, Profile("jake", Some("my bio"), Some("me.jpg"), following = false))
    ),
    (
      "R1.5",
      "rejects an unregistered username as not found",
      fixture =>
        for
          _ <- celebrity(fixture)
          error <- rejected(fixture.profiles.viewProfile(caller = None, "nobody"))
        yield assertEquals(error, ProfileError.NoSuchProfile)
    )
  )
