package realworld.comments.entity

/** A rejection this BC raises on its own account.
  *
  * There is no unauthenticated case: a caller that cannot be identified is rejected by `users` while its
  * token is being resolved, which keeps system invariant S1 stated in exactly one place.
  */
enum CommentError:
  /** R1.3 */
  case BlankBody

  /** R1.4, R2.4, R3.5 — the article itself is gone, which is a different absence from the comment's. */
  case NoSuchArticle

  /** R3.3 */
  case NoSuchComment

  /** R3.2 — the caller is authenticated, but the comment is somebody else's. */
  case NotTheAuthor
