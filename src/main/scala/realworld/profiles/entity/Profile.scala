package realworld.profiles.entity

/** The public face of an account, as one particular caller sees it.
  *
  * `following` is the only part `profiles` owns outright; the rest is `users`' account state, read through
  * `describe-user` (decision D10).
  */
final case class Profile(username: String, bio: Option[String], image: Option[String], following: Boolean)
