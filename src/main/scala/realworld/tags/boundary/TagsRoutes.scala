package realworld.tags.boundary

import cats.Monad
import cats.syntax.all.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.dsl.Http4sDsl

/** Exposes the `tags` boundary over HTTP.
  *
  * Only `list-tags` is reachable this way. `register-tags` is called by `articles`, never by a client.
  */
final class TagsRoutes[F[_]: Monad](tags: Tags[F]) extends Http4sDsl[F]:

  val routes: HttpRoutes[F] = HttpRoutes.of[F] { case GET -> Root / "api" / "tags" =>
    tags.listTags.flatMap(inUse => Ok(TagsBody.from(inUse)))
  }
