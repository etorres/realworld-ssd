package realworld.profiles.entity

/** A rejection this BC raises on its own account.
  *
  * There is no unauthenticated case: a caller that cannot be identified is rejected by `users` while its
  * token is being resolved, which is what keeps system invariant S1 stated in exactly one place.
  */
enum ProfileError:
  /** No account is registered under the requested username (R1.5, R2.4, R3.3). */
  case NoSuchProfile

  /** The caller named themselves (R2.3). */
  case CannotFollowSelf
