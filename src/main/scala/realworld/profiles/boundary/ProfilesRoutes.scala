package realworld.profiles.boundary

import cats.effect.kernel.Concurrent
import cats.mtl.{Handle, Raise}
import cats.syntax.all.*
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Response}

import realworld.profiles.entity.{Profile, ProfileError}
import realworld.support.http.ErrorEnvelope
import realworld.users.boundary.{CallerAuth, UserErrorRendering}
import realworld.users.entity.UserError

/** Exposes the `profiles` boundary over HTTP.
  *
  * Two typed error channels meet here: this component's own rejections, and the `users` rejection raised
  * while the caller's token is being resolved. Both are closed before a response leaves.
  */
final class ProfilesRoutes[F[_]: Concurrent](profiles: Profiles[F], caller: CallerAuth[F])
    extends Http4sDsl[F]:

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case request @ GET -> Root / "api" / "profiles" / username =>
      handled:
        for
          reader <- caller.optional(request)
          profile <- profiles.viewProfile(reader, username)
          response <- Ok(body(profile))
        yield response

    case request @ POST -> Root / "api" / "profiles" / username / "follow" =>
      handled:
        for
          follower <- caller.require(request)
          profile <- profiles.followUser(follower, username)
          response <- Ok(body(profile))
        yield response

    case request @ DELETE -> Root / "api" / "profiles" / username / "follow" =>
      handled:
        for
          follower <- caller.require(request)
          profile <- profiles.unfollowUser(follower, username)
          response <- Ok(body(profile))
        yield response
  }

  private def body(profile: Profile): ProfileBody = ProfileBody(ProfilePayload.from(profile))

  /** Opens both error scopes. The `users` one is outermost, so a caller who cannot be identified is
    * rejected before this component is asked anything — which is what makes an unauthenticated follow of
    * an unknown user a 401 rather than a 404.
    */
  private def handled(
      route: (Raise[F, ProfileError], Raise[F, UserError]) ?=> F[Response[F]]
  ): F[Response[F]] =
    Handle
      .allow[UserError](Handle.allow[ProfileError](route).rescue(rejection))
      .rescue(error => UserErrorRendering.response[F](error).pure[F])

  private def rejection(error: ProfileError): F[Response[F]] = error match
    case ProfileError.NoSuchProfile => NotFound(ErrorEnvelope.of(List("profile" -> "not found")))
    case ProfileError.CannotFollowSelf =>
      UnprocessableContent(ErrorEnvelope.of(List("username" -> "can't be yourself")))
