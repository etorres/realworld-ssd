package realworld.articles.entity

import cats.data.NonEmptyChain

/** A rejection this BC raises on its own account.
  *
  * There is no unauthenticated case: a caller that cannot be identified is rejected by `users` while its
  * token is being resolved, which keeps system invariant S1 stated in exactly one place.
  */
enum ArticleError:
  /** R1.6, R5.3 — the named fields were submitted blank. */
  case Blank(fields: NonEmptyChain[String])

  /** R5.10 — a tag list was submitted, but with no value. */
  case TagListWithoutValue

  /** R4.3, R5.5, R6.3, R7.3, R8.3 */
  case NoSuchArticle

  /** R5.4, R6.2 — the caller is authenticated, but the article is somebody else's. */
  case NotTheAuthor
