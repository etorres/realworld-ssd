package realworld.comments.control

import cats.effect.kernel.{Ref, Sync}
import cats.syntax.all.*

import realworld.articles.entity.ArticleId
import realworld.comments.entity.{Comment, CommentId}

/** Storage for comments, and the source of their identifiers. */
trait CommentRepository[F[_]]:
  /** R1.2 — unique across every comment, including ones already withdrawn. */
  def nextId: F[CommentId]

  def add(comment: Comment): F[Unit]
  def findById(id: CommentId): F[Option[Comment]]
  def remove(id: CommentId): F[Unit]

  /** Every comment on the article, oldest first, so a stable sort by creation time breaks ties. */
  def forArticle(article: ArticleId): F[List[Comment]]

object CommentRepository:

  /** In-memory storage, per decision D3. The counter never rewinds, so a withdrawn comment's identifier
    * is not handed out again.
    */
  def inMemory[F[_]: Sync]: F[CommentRepository[F]] =
    for
      issued <- Ref.of[F, Long](0L)
      store <- Ref.of[F, Vector[Comment]](Vector.empty)
    yield new CommentRepository[F]:
      def nextId: F[CommentId] = issued.updateAndGet(_ + 1).map(CommentId.apply)

      def add(comment: Comment): F[Unit] = store.update(_ :+ comment)

      def findById(id: CommentId): F[Option[Comment]] = store.get.map(_.find(_.id == id))

      def remove(id: CommentId): F[Unit] = store.update(_.filterNot(_.id == id))

      def forArticle(article: ArticleId): F[List[Comment]] =
        store.get.map(_.filter(_.article == article).toList)
