package realworld.support.http

import cats.Monad
import cats.data.Kleisli
import cats.syntax.all.*
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{HttpApp, HttpRoutes, Response, Status}

/** Composes the business components' routes into one application.
  *
  * Infrastructure only: it carries no capability behaviour, which is why it lives outside every spec (see
  * the adapter exceptions in `AGENTS.md`). It does own the fallthrough, so that an unrouted request is
  * still rendered as an error envelope (S2).
  */
object Api:
  def apply[F[_]: Monad](routes: HttpRoutes[F]*): HttpApp[F] =
    val combined = routes.foldLeft(HttpRoutes.empty[F])(_ <+> _)
    Kleisli: request =>
      combined.run(request).getOrElse(Response[F](Status.NotFound).withEntity(ErrorEnvelope.notFound))
