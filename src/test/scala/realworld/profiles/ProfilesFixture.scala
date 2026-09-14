package realworld.profiles

import cats.effect.IO
import cats.mtl.Raise
import org.http4s.{HttpApp, Method, Request, Response, Uri}

import realworld.Rejections
import realworld.profiles.boundary.{Profiles, ProfilesRoutes}
import realworld.profiles.entity.ProfileError
import realworld.support.http.Api
import realworld.users.boundary.{CallerAuth, RegisterUser, UpdateUser, Users, UsersRoutes}
import realworld.users.control.{FieldUpdate, Session}
import realworld.users.entity.{AuthToken, UserError}
import realworld.users.UsersFixture

/** Everything the `profiles` requirement suites arrange.
  *
  * A real `users` component is assembled alongside, because `profiles` reads account fields through
  * `describe-user` (decision D10) — a stub there would test the wrong thing.
  */
final case class ProfilesFixture(users: Users[IO], profiles: Profiles[IO], api: HttpApp[IO])

object ProfilesFixture:

  def apply(): IO[ProfilesFixture] =
    for
      users <- Users.inMemory[IO](UsersFixture.JwtSecret, UsersFixture.TokenTtl, UsersFixture.BcryptLogRounds)
      profiles <- Profiles.inMemory[IO](users)
    yield
      val caller = CallerAuth(users)
      val api = Api(UsersRoutes(users, caller).routes, ProfilesRoutes(profiles, caller).routes)
      ProfilesFixture(users, profiles, api)

  def accepted[A](operation: Raise[IO, ProfileError] ?=> IO[A]): IO[A] =
    Rejections.accepted[ProfileError, A](operation)

  def rejected[A](operation: Raise[IO, ProfileError] ?=> IO[A]): IO[ProfileError] =
    Rejections.rejected[ProfileError, A](operation)

  extension (fixture: ProfilesFixture)
    def register(username: String, email: String): IO[Session] =
      Rejections.accepted[UserError, Session](
        fixture.users.registerUser(RegisterUser(username, email, "password123"))
      )

    /** A registered account with a bio and an image, so a row can tell that `view-profile` really carries
      * the account's own fields rather than blanks.
      */
    def registerDescribed(username: String, email: String, bio: String, image: String): IO[Session] =
      for
        session <- fixture.register(username, email)
        updated <- Rejections.accepted[UserError, Session](
          fixture.users.updateUser(
            session.user.id,
            UpdateUser(
              email = FieldUpdate.Unchanged,
              username = FieldUpdate.Unchanged,
              password = FieldUpdate.Unchanged,
              bio = FieldUpdate.Assigned(bio),
              image = FieldUpdate.Assigned(image)
            )
          )
        )
      yield updated

    def call(method: Method, path: String, token: Option[AuthToken] = None): IO[Response[IO]] =
      val base = Request[IO](method, Uri.unsafeFromString(path))
      fixture.api.run(token.fold(base)(t => base.putHeaders("Authorization" -> s"Token ${t.value}")))
