package realworld.tags.boundary

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.Codec

import realworld.tags.entity.Tag

/** The wire shape of the `tags` boundary, exactly as the RealWorld API defines it. */
final case class TagsBody(tags: List[String])

object TagsBody:
  given Codec.AsObject[TagsBody] = KindlingsCodecAsObject.derived

  def from(tags: List[Tag]): TagsBody = TagsBody(tags.map(_.value))
