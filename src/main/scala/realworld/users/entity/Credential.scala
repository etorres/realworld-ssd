package realworld.users.entity

/** The irreversible form of a user's password.
  *
  * R1.6 makes this the only representation the BC is allowed to retain, and forbids disclosing even this
  * one.
  */
final case class Credential(hash: HashedPassword)

opaque type HashedPassword = String

object HashedPassword:
  def fromHash(value: String): HashedPassword = value

  extension (hashed: HashedPassword) def value: String = hashed
