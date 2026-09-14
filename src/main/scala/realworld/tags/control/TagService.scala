package realworld.tags.control

import realworld.tags.entity.Tag

/** The `tags` use cases. */
final class TagService[F[_]](registry: TagRegistry[F]):

  /** Unusable names are dropped rather than rejected (R1.3), so `articles` need not sanitise first. */
  def register(names: List[String]): F[Unit] = registry.add(names.flatMap(Tag.parse))

  def list: F[List[Tag]] = registry.all
