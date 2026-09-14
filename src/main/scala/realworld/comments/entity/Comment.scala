package realworld.comments.entity

import java.time.Instant

import realworld.articles.entity.ArticleId
import realworld.users.entity.UserId

/** A reply attached to one article.
  *
  * Both the article and the author are held by identity rather than by slug or name, because both of
  * those can change (`articles` R5.2, `users` R4.1) while the reference must not.
  */
final case class Comment(
    id: CommentId,
    article: ArticleId,
    body: String,
    author: UserId,
    createdAt: Instant,
    updatedAt: Instant
)

/** R1.2 — a whole number, because the identifier is addressed in a request path and read back as one. */
opaque type CommentId = Long

object CommentId:
  def apply(value: Long): CommentId = value

  extension (id: CommentId) def value: Long = id
