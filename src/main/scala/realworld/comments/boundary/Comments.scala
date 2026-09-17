package realworld.comments.boundary

import cats.effect.kernel.{Concurrent, Resource, Sync}
import cats.mtl.Raise
import cats.syntax.all.*

import realworld.articles.boundary.Articles
import realworld.comments.control.{CommentRepository, CommentService, CommentView}
import realworld.comments.entity.CommentError
import realworld.profiles.boundary.Profiles
import realworld.users.entity.UserId

/** The contract of the `comments` business component. Each method realises one `## Boundary` operation. */
trait Comments[F[_]]:
  def addComment(caller: UserId, slug: String, body: String)(using Raise[F, CommentError]): F[CommentView]

  /** The caller is optional: an anonymous reader sees the comments with every follow flag unset. */
  def listComments(caller: Option[UserId], slug: String)(using
      Raise[F, CommentError]
  ): F[List[CommentView]]

  def deleteComment(caller: UserId, slug: String, id: Long)(using Raise[F, CommentError]): F[Unit]

object Comments:

  def apply[F[_]](service: CommentService[F]): Comments[F] = new Comments[F]:

    def addComment(caller: UserId, slug: String, body: String)(using
        Raise[F, CommentError]
    ): F[CommentView] = service.add(caller, slug, body)

    def listComments(caller: Option[UserId], slug: String)(using
        Raise[F, CommentError]
    ): F[List[CommentView]] = service.list(caller, slug)

    def deleteComment(caller: UserId, slug: String, id: Long)(using Raise[F, CommentError]): F[Unit] =
      service.delete(caller, slug, id)

  /** The whole component, assembled over in-memory storage (decision D3). */
  def inMemory[F[_]: Sync](articles: Articles[F], profiles: Profiles[F]): F[Comments[F]] =
    CommentRepository.inMemory[F].map(comments => apply(CommentService(comments, articles, profiles)))

  /** The whole component, assembled over PostgreSQL (decision D11). */
  def postgres[F[_]: Concurrent: cats.effect.kernel.Clock](
      pool: Resource[F, skunk.Session[F]],
      articles: Articles[F],
      profiles: Profiles[F]
  ): Comments[F] =
    apply(CommentService(CommentRepository.postgres(pool), articles, profiles))
