package realworld.comments.control

import java.time.{Instant, OffsetDateTime, ZoneOffset}
import java.util.UUID

import cats.effect.kernel.{Concurrent, Ref, Resource, Sync}
import cats.syntax.all.*
import skunk.codec.all.{int8, text, timestamptz, uuid}
import skunk.implicits.*
import skunk.{Codec, Command, Query, Session, Void}

import realworld.articles.entity.ArticleId
import realworld.comments.entity.{Comment, CommentId}
import realworld.users.entity.UserId

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

  /** PostgreSQL storage, per decision D11. A sequence replaces the counter, and never rewinds either. */
  def postgres[F[_]: Concurrent](pool: Resource[F, Session[F]]): CommentRepository[F] =
    new CommentRepository[F]:
      def nextId: F[CommentId] = pool.use(_.unique(NextId)).map(CommentId.apply)

      def add(comment: Comment): F[Unit] = pool.use(_.prepare(Insert).flatMap(_.execute(comment))).void

      def findById(id: CommentId): F[Option[Comment]] =
        pool.use(_.prepare(ById).flatMap(_.option(id.value)))

      def remove(id: CommentId): F[Unit] = pool.use(_.prepare(Delete).flatMap(_.execute(id.value))).void

      def forArticle(article: ArticleId): F[List[Comment]] =
        pool.use(_.prepare(ForArticle).flatMap(_.stream(article.value, 64).compile.toList))

  /** The tables this component owns. The sequence is separate from the table because the identifier is
    * handed out before the row exists: `CommentService` builds the whole `Comment` and then stores it.
    */
  val Tables: List[Command[Void]] =
    List(
      sql"CREATE SEQUENCE IF NOT EXISTS comment_ids AS bigint START 1".command,
      sql"""CREATE TABLE IF NOT EXISTS comments (
              id         bigint PRIMARY KEY,
              article    uuid NOT NULL,
              body       text NOT NULL,
              author     uuid NOT NULL,
              created_at timestamptz NOT NULL,
              updated_at timestamptz NOT NULL
            )""".command
    )

  private val comment: Codec[Comment] =
    (int8 *: uuid *: text *: uuid *: timestamptz *: timestamptz).imap {
      case (id, article, body, author, createdAt, updatedAt) =>
        Comment(
          id = CommentId(id),
          article = ArticleId(article),
          body = body,
          author = UserId(author),
          createdAt = createdAt.toInstant,
          updatedAt = updatedAt.toInstant
        )
    }(row =>
      (
        row.id.value,
        row.article.value,
        row.body,
        row.author.value,
        utc(row.createdAt),
        utc(row.updatedAt)
      )
    )

  private def utc(instant: Instant): OffsetDateTime = instant.atOffset(ZoneOffset.UTC)

  private val Columns = "id, article, body, author, created_at, updated_at"

  private val NextId: Query[Void, Long] = sql"SELECT nextval('comment_ids')".query(int8)

  private val Insert: Command[Comment] = sql"INSERT INTO comments (#$Columns) VALUES ($comment)".command

  private val ById: Query[Long, Comment] =
    sql"SELECT #$Columns FROM comments WHERE id = $int8".query(comment)

  /** Oldest first, and the identifier breaks a tie.
    *
    * That second key is not decoration. Without it the order is the heap's, and a comment added after a
    * vacuum reclaimed a deleted row's slot comes back in that slot's place — measured, with the newest
    * comment arriving last. The identifier is safe to order by because R1.2 makes it a whole number this
    * BC hands out in sequence, so it already *is* the creation order; `articles` has no such column,
    * which is why hazard H1 stays open there.
    */
  private val ForArticle: Query[UUID, Comment] =
    sql"SELECT #$Columns FROM comments WHERE article = $uuid ORDER BY created_at, id".query(comment)

  private val Delete: Command[Long] = sql"DELETE FROM comments WHERE id = $int8".command
