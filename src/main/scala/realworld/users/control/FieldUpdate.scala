package realworld.users.control

/** One field of a partial update.
  *
  * The contract distinguishes three cases and they mean different things: a field left out keeps its
  * value (R4.1), while a field mentioned as empty clears it (R4.2) or is rejected where the field has no
  * cleared state (R4.7).
  */
enum FieldUpdate[+A]:
  case Unchanged
  case Cleared
  case Assigned(value: A)
