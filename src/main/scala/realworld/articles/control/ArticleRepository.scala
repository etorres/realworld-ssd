package realworld.articles.control

import java.time.{Instant, OffsetDateTime, ZoneOffset}

import cats.effect.kernel.{Concurrent, Ref, Resource, Sync}
import cats.syntax.all.*
import skunk.codec.all.{int8, text, timestamptz, uuid, _text}
import skunk.data.Arr
import skunk.implicits.*
import skunk.{Codec, Command, Query, Session, Void}

import realworld.articles.entity.{Article, ArticleId, Slug}
import realworld.users.entity.UserId

/** Storage for articles. Filtering and ordering belong to the service, which knows what the spec asks for. */
trait ArticleRepository[F[_]]:
  def findBySlug(slug: Slug): F[Option[Article]]
  def save(article: Article): F[Unit]
  def delete(id: ArticleId): F[Unit]

  /** Every article, oldest first, so that a stable sort by creation time breaks ties predictably. */
  def all: F[List[Article]]

object ArticleRepository:

  /** In-memory storage, per decision D3. */
  def inMemory[F[_]: Sync]: F[ArticleRepository[F]] =
    Ref
      .of[F, Vector[Article]](Vector.empty)
      .map: store =>
        new ArticleRepository[F]:
          def findBySlug(slug: Slug): F[Option[Article]] = store.get.map(_.find(_.slug == slug))

          def save(article: Article): F[Unit] =
            store.update: stored =>
              stored.indexWhere(_.id == article.id) match
                case -1 => stored :+ article
                case index => stored.updated(index, article)

          def delete(id: ArticleId): F[Unit] = store.update(_.filterNot(_.id == id))

          def all: F[List[Article]] = store.get.map(_.toList)

  /** PostgreSQL storage, per decision D11. */
  def postgres[F[_]: Concurrent](pool: Resource[F, Session[F]]): ArticleRepository[F] =
    new ArticleRepository[F]:
      def findBySlug(slug: Slug): F[Option[Article]] =
        pool.use(_.prepare(BySlug).flatMap(_.option(slug.value)))

      def save(article: Article): F[Unit] =
        pool.use(_.prepare(Upsert).flatMap(_.execute(article))).void

      def delete(id: ArticleId): F[Unit] =
        pool.use(_.prepare(Delete).flatMap(_.execute(id.value))).void

      def all: F[List[Article]] = pool.use(_.execute(All))

  /** The tables this component owns.
    *
    * `tags` is an array rather than a side table because R1.2 and R5.7 both fix the order the tags were
    * given in, and an array keeps that without a position column to maintain. No foreign key on `author`,
    * for the reason `profiles` has none either: `users` is reachable only through its boundary.
    */
  val Tables: List[Command[Void]] =
    List(
      sql"""CREATE TABLE IF NOT EXISTS articles (
              id          bigint PRIMARY KEY,
              slug        text NOT NULL UNIQUE,
              title       text NOT NULL,
              description text NOT NULL,
              body        text NOT NULL,
              tags        text[] NOT NULL,
              author      uuid NOT NULL,
              created_at  timestamptz NOT NULL,
              updated_at  timestamptz NOT NULL
            )""".command
    )

  private val article: Codec[Article] =
    (int8 *: text *: text *: text *: text *: _text *: uuid *: timestamptz *: timestamptz).imap {
      case (id, slug, title, description, body, tags, author, createdAt, updatedAt) =>
        Article(
          id = ArticleId(id),
          slug = Slug.of(slug),
          title = title,
          description = description,
          body = body,
          tags = tags.toList,
          author = UserId(author),
          createdAt = createdAt.toInstant,
          updatedAt = updatedAt.toInstant
        )
    }(row =>
      (
        row.id.value,
        row.slug.value,
        row.title,
        row.description,
        row.body,
        Arr.fromFoldable(row.tags),
        row.author.value,
        utc(row.createdAt),
        utc(row.updatedAt)
      )
    )

  private def utc(instant: Instant): OffsetDateTime = instant.atOffset(ZoneOffset.UTC)

  private val Columns = "id, slug, title, description, body, tags, author, created_at, updated_at"

  private val BySlug: Query[String, Article] =
    sql"SELECT #$Columns FROM articles WHERE slug = $text".query(article)

  /** Oldest first, and the identifier breaks a tie.
    *
    * Without that second key the order of articles sharing a creation instant is the heap's, and a
    * rewritten row moves to the end of it — which R5.2 does on every title change. `ArticleId` is a TSID,
    * so it already carries the order they were created in.
    */
  private val All: Query[Void, Article] =
    sql"SELECT #$Columns FROM articles ORDER BY created_at, id".query(article)

  private val Upsert: Command[Article] =
    sql"""INSERT INTO articles (#$Columns) VALUES ($article)
          ON CONFLICT (id) DO UPDATE SET
            slug        = EXCLUDED.slug,
            title       = EXCLUDED.title,
            description = EXCLUDED.description,
            body        = EXCLUDED.body,
            tags        = EXCLUDED.tags,
            updated_at  = EXCLUDED.updated_at""".command

  private val Delete: Command[Long] = sql"DELETE FROM articles WHERE id = $int8".command
