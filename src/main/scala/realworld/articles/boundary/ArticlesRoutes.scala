package realworld.articles.boundary

import cats.effect.kernel.Concurrent
import cats.mtl.syntax.all.*
import cats.mtl.{Handle, Raise}
import cats.syntax.all.*
import io.circe.Decoder
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher
import org.http4s.{HttpRoutes, Request, Response, Status}

import realworld.articles.control.{ArticleFilter, Page}
import realworld.articles.entity.ArticleError
import realworld.support.http.ErrorEnvelope
import realworld.users.boundary.{CallerAuth, UserErrorRendering}
import realworld.users.entity.UserError

/** Exposes the `articles` boundary over HTTP.
  *
  * Two typed error channels meet here: this component's own rejections, and the `users` rejection raised
  * while the caller's token is being resolved. Both are closed before a response leaves.
  */
final class ArticlesRoutes[F[_]: Concurrent](articles: Articles[F], caller: CallerAuth[F])
    extends Http4sDsl[F]:

  import ArticlesRoutes.*

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    // Ahead of the slug route: `feed` would otherwise be read as the slug of an article.
    case request @ GET -> Root / "api" / "articles" / "feed" :? LimitParam(limit) +& OffsetParam(offset) =>
      handled:
        for
          reader <- caller.require(request)
          page <- articles.readFeed(reader, Page.of(limit, offset))
          response <- Ok(ArticlesBody.from(page))
        yield response

    case request @ GET -> Root / "api" / "articles" :? TagParam(tag) +& AuthorParam(author) +&
        FavoritedParam(favorited) +& LimitParam(limit) +& OffsetParam(offset) =>
      handled:
        for
          reader <- caller.optional(request)
          page <- articles.listArticles(reader, ArticleFilter(tag, author, favorited), Page.of(limit, offset))
          response <- Ok(ArticlesBody.from(page))
        yield response

    case request @ POST -> Root / "api" / "articles" =>
      handled:
        for
          author <- caller.require(request)
          body <- decoded[PublishRequest](request)
          payload = body.article
          command = PublishArticle(
            payload.title.orEmpty,
            payload.description.orEmpty,
            payload.body.orEmpty,
            payload.tagList
          )
          view <- articles.publishArticle(author, command)
          response <- Created(ArticleBody.from(view))
        yield response

    case request @ GET -> Root / "api" / "articles" / slug =>
      handled:
        for
          reader <- caller.optional(request)
          view <- articles.readArticle(reader, slug)
          response <- Ok(ArticleBody.from(view))
        yield response

    case request @ PUT -> Root / "api" / "articles" / slug =>
      handled:
        for
          author <- caller.require(request)
          body <- decoded[UpdateRequest](request)
          payload = body.article
          command = UpdateArticle(payload.title, payload.description, payload.body, payload.tags)
          view <- articles.updateArticle(author, slug, command)
          response <- Ok(ArticleBody.from(view))
        yield response

    case request @ DELETE -> Root / "api" / "articles" / slug =>
      handled:
        for
          author <- caller.require(request)
          _ <- articles.deleteArticle(author, slug)
          response <- NoContent()
        yield response

    case request @ POST -> Root / "api" / "articles" / slug / "favorite" =>
      handled:
        for
          reader <- caller.require(request)
          view <- articles.favoriteArticle(reader, slug)
          response <- Ok(ArticleBody.from(view))
        yield response

    case request @ DELETE -> Root / "api" / "articles" / slug / "favorite" =>
      handled:
        for
          reader <- caller.require(request)
          view <- articles.unfavoriteArticle(reader, slug)
          response <- Ok(ArticleBody.from(view))
        yield response
  }

  /** Opens both error scopes. The `users` one is outermost, so a caller who cannot be identified is
    * rejected before this component is asked anything — which is what makes an unauthenticated request
    * about an unknown slug a 401 rather than a 404.
    */
  private def handled(
      route: (Raise[F, ArticleError], Raise[F, UserError]) ?=> F[Response[F]]
  ): F[Response[F]] =
    Handle
      .allow[UserError](Handle.allow[ArticleError](route).rescue(rejection))
      .rescue(error => UserErrorRendering.response[F](error).pure[F])

  private def rejection(error: ArticleError): F[Response[F]] = error match
    case ArticleError.NoSuchArticle => NotFound(ErrorEnvelope.of(List("article" -> "not found")))
    case ArticleError.NotTheAuthor =>
      Response[F](Status.Forbidden).withEntity(ErrorEnvelope.of(List("article" -> "forbidden"))).pure[F]
    case ArticleError.Blank(fields) =>
      UnprocessableContent(ErrorEnvelope.of(fields.toList.map(_ -> "can't be blank")))
    case ArticleError.TagListWithoutValue =>
      UnprocessableContent(ErrorEnvelope.of(List("tagList" -> "is invalid")))

  /** An unreadable body is a validation failure with the standard envelope, not http4s' default response. */
  private def decoded[A: Decoder](request: Request[F])(using Raise[F, ArticleError]): F[A] =
    request
      .attemptAs[A]
      .leftMap(_ => ArticleError.Blank(cats.data.NonEmptyChain.one("body")))
      .value
      .flatMap(_.fold(_.raise[F, A], _.pure[F]))

object ArticlesRoutes:
  object TagParam extends OptionalQueryParamDecoderMatcher[String]("tag")
  object AuthorParam extends OptionalQueryParamDecoderMatcher[String]("author")
  object FavoritedParam extends OptionalQueryParamDecoderMatcher[String]("favorited")
  object LimitParam extends OptionalQueryParamDecoderMatcher[Int]("limit")
  object OffsetParam extends OptionalQueryParamDecoderMatcher[Int]("offset")
