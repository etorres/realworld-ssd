package realworld.users.control

import realworld.users.entity.UserId

/** What `users` is willing to say about an account to anybody who asks (R6.1).
  *
  * The type exists so that R6.3 is structural rather than a matter of discipline: there is no email and no
  * credential here to leak.
  */
final case class PublicAccount(id: UserId, username: String, bio: Option[String], image: Option[String])
