package realworld.users.entity

/** A bearer token this BC issued, proving the holder is a particular account.
  *
  * Issuing and verifying are both owned here — see decision D4 in the system doc.
  */
opaque type AuthToken = String

object AuthToken:
  def apply(value: String): AuthToken = value

  extension (token: AuthToken) def value: String = token
