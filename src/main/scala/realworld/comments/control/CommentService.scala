package realworld.comments.control

import cats.MonadThrow
import cats.effect.kernel.Clock
import cats.mtl.syntax.all.*
import cats.mtl.{Handle, Raise}
import cats.syntax.all.*

import realworld.articles.boundary.Articles
import realworld.articles.entity.{ArticleError, ArticleId}
import realworld.comments.entity.{Comment, CommentError, CommentId}
import realworld.profiles.boundary.Profiles
import realworld.profiles.entity.Profile
import realworld.users.entity.UserId

/** One comment as a particular caller sees it: the reply, and its author rendered by `profiles`. */
final case class CommentView(comment: Comment, author: Profile)

/** The `comments` use cases. */
final class CommentService[F[_]: MonadThrow: Clock](
    comments: CommentRepository[F],
    articles: Articles[F],
    profiles: Profiles[F]
):

  def add(caller: UserId, slug: String, body: String)(using Raise[F, CommentError]): F[CommentView] =
    for
      article <- existingArticle(slug)
      _ <- CommentError.BlankBody.raise[F, Unit].whenA(body.trim.isEmpty)
      now <- Clock[F].realTimeInstant
      id <- comments.nextId
      comment = Comment(id, article, body, caller, now, now)
      _ <- comments.add(comment)
      view <- viewOf(comment, caller.some)
    yield view

  def list(caller: Option[UserId], slug: String)(using Raise[F, CommentError]): F[List[CommentView]] =
    for
      article <- existingArticle(slug)
      attached <- comments.forArticle(article)
      // Newest first (R2.1). The sort is stable and the repository yields insertion order, so comments
      // created within the same clock tick still come back newest first.
      views <- attached.sortBy(_.createdAt.toEpochMilli).reverse.traverse(viewOf(_, caller))
    yield views

  def delete(caller: UserId, slug: String, id: Long)(using Raise[F, CommentError]): F[Unit] =
    for
      // R3.5 before R3.3: a stale slug is told the article is gone, not the comment.
      article <- existingArticle(slug)
      found <- comments.findById(CommentId(id))
      comment <- found
        .filter(_.article == article)
        .fold(CommentError.NoSuchComment.raise[F, Comment])(_.pure[F])
      _ <- CommentError.NotTheAuthor.raise[F, Unit].whenA(comment.author != caller)
      _ <- comments.remove(comment.id)
    yield ()

  /** Asks `articles` whether the slug resolves, and restates its rejection as this BC's own. Unlike a
    * missing author, a missing article is an ordinary thing for a caller to ask about.
    */
  private def existingArticle(slug: String)(using Raise[F, CommentError]): F[ArticleId] =
    Handle
      .allow[ArticleError](articles.readArticle(caller = None, slug).map(_.article.id.some))
      .rescue(_ => Option.empty[ArticleId].pure[F])
      .flatMap(_.fold(CommentError.NoSuchArticle.raise[F, ArticleId])(_.pure[F]))

  private def viewOf(comment: Comment, caller: Option[UserId]): F[CommentView] =
    profiles.viewAuthor(comment.author, caller).flatMap {
      case Some(profile) => CommentView(comment, profile).pure[F]
      case None =>
        MonadThrow[F].raiseError[CommentView](
          IllegalStateException(s"comment ${comment.id.value} references an account that does not exist")
        )
    }
