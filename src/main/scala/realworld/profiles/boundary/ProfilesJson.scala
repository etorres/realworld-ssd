package realworld.profiles.boundary

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.Codec

import realworld.profiles.entity.Profile

/** The wire shape of the `profiles` boundary, exactly as the RealWorld API defines it. */
final case class ProfilePayload(
    username: String,
    bio: Option[String],
    image: Option[String],
    following: Boolean
)

object ProfilePayload:
  given Codec.AsObject[ProfilePayload] = KindlingsCodecAsObject.derived

  def from(profile: Profile): ProfilePayload =
    ProfilePayload(profile.username, profile.bio, profile.image, profile.following)

final case class ProfileBody(profile: ProfilePayload)

object ProfileBody:
  given Codec.AsObject[ProfileBody] = KindlingsCodecAsObject.derived
