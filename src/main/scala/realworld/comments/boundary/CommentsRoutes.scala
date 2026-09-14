package realworld.comments.boundary

import cats.effect.kernel.Concurrent
import cats.mtl.syntax.all.*
import cats.mtl.{Handle, Raise}
import cats.syntax.all.*
import io.circe.Decoder
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Request, Response, Status}

import realworld.comments.entity.CommentError
import realworld.support.http.ErrorEnvelope
import realworld.users.boundary.{CallerAuth, UserErrorRendering}
import realworld.users.entity.UserError

/** Exposes the `comments` boundary over HTTP.
  *
  * Two typed error channels meet here: this component's own rejections, and the `users` rejection raised
  * while the caller's token is being resolved. Both are closed before a response leaves.
  */
final class CommentsRoutes[F[_]: Concurrent](comments: Comments[F], caller: CallerAuth[F])
    extends Http4sDsl[F]:

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case request @ POST -> Root / "api" / "articles" / slug / "comments" =>
      handled:
        for
          author <- caller.require(request)
          body <- decoded[AddCommentRequest](request)
          view <- comments.addComment(author, slug, body.comment.body.orEmpty)
          response <- Created(CommentBody.from(view))
        yield response

    case request @ GET -> Root / "api" / "articles" / slug / "comments" =>
      handled:
        for
          reader <- caller.optional(request)
          views <- comments.listComments(reader, slug)
          response <- Ok(CommentsBody.from(views))
        yield response

    case request @ DELETE -> Root / "api" / "articles" / slug / "comments" / LongVar(id) =>
      handled:
        for
          author <- caller.require(request)
          _ <- comments.deleteComment(author, slug, id)
          response <- NoContent()
        yield response
  }

  /** Opens both error scopes. The `users` one is outermost, so a caller who cannot be identified is
    * rejected before this component is asked anything — which is what makes an unauthenticated request
    * about an unknown slug a 401 rather than a 404.
    */
  private def handled(
      route: (Raise[F, CommentError], Raise[F, UserError]) ?=> F[Response[F]]
  ): F[Response[F]] =
    Handle
      .allow[UserError](Handle.allow[CommentError](route).rescue(rejection))
      .rescue(error => UserErrorRendering.response[F](error).pure[F])

  private def rejection(error: CommentError): F[Response[F]] = error match
    case CommentError.BlankBody => UnprocessableContent(ErrorEnvelope.of(List("body" -> "can't be blank")))
    case CommentError.NoSuchArticle => NotFound(ErrorEnvelope.of(List("article" -> "not found")))
    case CommentError.NoSuchComment => NotFound(ErrorEnvelope.of(List("comment" -> "not found")))
    case CommentError.NotTheAuthor =>
      Response[F](Status.Forbidden).withEntity(ErrorEnvelope.of(List("comment" -> "forbidden"))).pure[F]

  /** An unreadable body is a validation failure with the standard envelope, not http4s' default response. */
  private def decoded[A: Decoder](request: Request[F])(using Raise[F, CommentError]): F[A] =
    request.attemptAs[A].leftMap(_ => CommentError.BlankBody).value.flatMap(_.fold(_.raise[F, A], _.pure[F]))
