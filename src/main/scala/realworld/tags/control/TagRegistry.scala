package realworld.tags.control

import cats.effect.kernel.{Concurrent, Ref, Resource, Sync}
import cats.syntax.all.*
import skunk.codec.all.text
import skunk.implicits.*
import skunk.{Command, Query, Session, Void}

import realworld.tags.entity.Tag

/** The set of tags in use. Append-only, per decision D5 in the system doc. */
trait TagRegistry[F[_]]:
  def add(tags: List[Tag]): F[Unit]
  def all: F[List[Tag]]

object TagRegistry:

  /** In-memory storage, per decision D3.
    *
    * Held as a vector of distinct tags rather than a set: the spec fixes only that each tag is reported
    * once (R2.1), and reporting them in the order they came into use is stabler than whatever order a hash
    * set happens to produce.
    */
  def inMemory[F[_]: Sync]: F[TagRegistry[F]] =
    Ref
      .of[F, Vector[Tag]](Vector.empty)
      .map: store =>
        new TagRegistry[F]:
          def add(tags: List[Tag]): F[Unit] = store.update(inUse => (inUse ++ tags).distinct)

          def all: F[List[Tag]] = store.get.map(_.toList)

  /** PostgreSQL storage, per decision D11.
    *
    * The primary key carries R1.2 the way the in-memory `distinct` did: registering a tag already in use
    * is a conflict the insert is told to ignore, rather than a read the service has to perform first.
    */
  def postgres[F[_]: Concurrent](pool: Resource[F, Session[F]]): TagRegistry[F] =
    new TagRegistry[F]:
      def add(tags: List[Tag]): F[Unit] =
        pool.use: session =>
          session.prepare(Insert).flatMap(insert => tags.traverse_(tag => insert.execute(tag.value)))

      def all: F[List[Tag]] =
        // A blank name cannot reach the table, since `TagService` drops it first (R1.3), so `parse`
        // yielding nothing would mean the table had been written to by something other than this BC.
        pool.use(_.execute(All)).map(_.flatMap(Tag.parse))

  /** The tables this component owns. */
  val Tables: List[Command[Void]] =
    List(sql"CREATE TABLE IF NOT EXISTS tags (name text PRIMARY KEY)".command)

  private val Insert: Command[String] =
    sql"INSERT INTO tags (name) VALUES ($text) ON CONFLICT (name) DO NOTHING".command

  private val All: Query[Void, String] = sql"SELECT name FROM tags".query(text)
