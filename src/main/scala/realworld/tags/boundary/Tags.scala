package realworld.tags.boundary

import cats.effect.kernel.{Concurrent, Resource, Sync}
import cats.syntax.all.*
import skunk.Session

import realworld.tags.control.{TagRegistry, TagService}
import realworld.tags.entity.Tag

/** The contract of the `tags` business component.
  *
  * Neither operation asks for a typed error channel, because neither can be rejected: an unusable tag is
  * ignored (R1.3), and an empty registry is an empty answer rather than a failure (R2.2).
  */
trait Tags[F[_]]:
  /** Called by `articles` when an article declares its tags. */
  def registerTags(names: List[String]): F[Unit]

  def listTags: F[List[Tag]]

object Tags:

  def apply[F[_]](service: TagService[F]): Tags[F] = new Tags[F]:
    def registerTags(names: List[String]): F[Unit] = service.register(names)

    def listTags: F[List[Tag]] = service.list

  /** The whole component, assembled over in-memory storage (decision D3). */
  def inMemory[F[_]: Sync]: F[Tags[F]] =
    TagRegistry.inMemory[F].map(registry => apply(TagService(registry)))

  /** The whole component, assembled over PostgreSQL (decision D11). Nothing above the registry changes:
    * `TagService` is the same object either way, which is the point of the comparison.
    */
  def postgres[F[_]: Concurrent](pool: Resource[F, Session[F]]): Tags[F] =
    apply(TagService(TagRegistry.postgres(pool)))
