package realworld.users.boundary

import cats.effect.kernel.Concurrent
import cats.mtl.syntax.all.*
import cats.mtl.{Handle, Raise}
import cats.syntax.all.*
import io.circe.Decoder
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Request, Response}

import realworld.users.control.Session
import realworld.users.entity.{FieldError, UserError}

/** Exposes the `users` boundary over HTTP and closes the typed error scope: everything a route calls may
  * raise a `UserError`, and nothing escapes without being rendered per system invariant S2.
  */
final class UsersRoutes[F[_]: Concurrent](users: Users[F], caller: CallerAuth[F]) extends Http4sDsl[F]:

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case request @ POST -> Root / "api" / "users" =>
      handled:
        for
          body <- decoded[RegisterBody](request)
          payload = body.user
          command = RegisterUser(payload.username.orEmpty, payload.email.orEmpty, payload.password.orEmpty)
          session <- users.registerUser(command)
          response <- Created(rendered(session))
        yield response

    case request @ POST -> Root / "api" / "users" / "login" =>
      handled:
        for
          body <- decoded[LoginBody](request)
          payload = body.user
          session <- users.authenticateUser(Credentials(payload.email.orEmpty, payload.password.orEmpty))
          response <- Ok(rendered(session))
        yield response

    case request @ GET -> Root / "api" / "user" =>
      handled:
        for
          id <- caller.require(request)
          session <- users.getCurrentUser(id)
          response <- Ok(rendered(session))
        yield response

    case request @ PUT -> Root / "api" / "user" =>
      handled:
        for
          id <- caller.require(request)
          body <- decoded[UpdateBody](request)
          payload = body.user
          command = UpdateUser(payload.email, payload.username, payload.password, payload.bio, payload.image)
          session <- users.updateUser(id, command)
          response <- Ok(rendered(session))
        yield response
  }

  private def rendered(session: Session): UserBody = UserBody(UserPayload.from(session))

  /** Opens the scope in which a `UserError` may be raised and guarantees every one is rendered. */
  private def handled(route: Raise[F, UserError] ?=> F[Response[F]]): F[Response[F]] =
    Handle.allow[UserError](route).rescue(error => UserErrorRendering.response[F](error).pure[F])

  /** An unreadable body is a validation failure with the standard envelope, not http4s' default response.
    * The typed error channel is not in play inside `attemptAs`, so this cannot swallow a raised error.
    */
  private def decoded[A: Decoder](request: Request[F])(using Raise[F, UserError]): F[A] =
    request
      .attemptAs[A]
      .leftMap(_ => UserError.invalid(FieldError.invalid("body")))
      .value
      .flatMap(_.fold(_.raise[F, A], _.pure[F]))
