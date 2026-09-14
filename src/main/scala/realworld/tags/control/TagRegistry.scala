package realworld.tags.control

import cats.effect.kernel.{Ref, Sync}
import cats.syntax.all.*

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
