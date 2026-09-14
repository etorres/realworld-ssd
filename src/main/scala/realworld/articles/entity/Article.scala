package realworld.articles.entity

import java.time.Instant
import java.util.UUID

import realworld.users.entity.UserId

/** An authored post. The author is held by identity rather than by name, because a name can change
  * (`users` R4.1) while the reference must not.
  */
final case class Article(
    id: ArticleId,
    slug: Slug,
    title: String,
    description: String,
    body: String,
    tags: List[String],
    author: UserId,
    createdAt: Instant,
    updatedAt: Instant
)

opaque type ArticleId = UUID

object ArticleId:
  def apply(value: UUID): ArticleId = value

  extension (id: ArticleId) def value: UUID = id

/** The URL-safe, unique identifier of an article. */
opaque type Slug = String

object Slug:
  /** The form derived from a title. A title of nothing but punctuation still needs a slug, so it falls
    * back to a constant, which R1.5 then makes distinct.
    */
  def from(title: String): Slug =
    val cleaned = title.trim.toLowerCase.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "")
    if cleaned.isEmpty then "article" else cleaned

  /** The nth candidate for a title whose earlier forms are already taken (R1.5). */
  def numbered(base: Slug, n: Int): Slug = s"$base-$n"

  /** The lookup form: taken as offered, since an unknown slug is a failed lookup, not a bad one. */
  def of(raw: String): Slug = raw

  extension (slug: Slug) def value: String = slug
