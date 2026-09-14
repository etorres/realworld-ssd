package realworld.tags.entity

/** A free-text label an article declares. */
opaque type Tag = String

object Tag:

  /** Yields nothing for a blank name, because R1.3 makes a blank tag something to ignore rather than
    * reject. Surrounding space is trimmed, since deciding whether a name is blank means looking past it
    * anyway.
    */
  def parse(raw: String): Option[Tag] =
    val trimmed = raw.trim
    Option.when(trimmed.nonEmpty)(trimmed)

  extension (tag: Tag) def value: String = tag
