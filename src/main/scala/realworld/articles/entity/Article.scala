package realworld.articles.entity

import java.time.Instant

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

/** A time-sorted identifier (a TSID): 42 bits of millisecond, then a node, then a counter.
  *
  * It replaced a random UUID so that the identifier carries the order the articles were created in.
  * `articles` had been borrowing that order — first from the insertion order of a `Vector`, then from
  * PostgreSQL's heap, which moves when a row is rewritten. Neither is a promise, and R2.1 needs one when
  * two articles share a creation instant. It is never serialised: an article is addressed by its slug.
  */
opaque type ArticleId = Long

object ArticleId:
  def apply(value: Long): ArticleId = value

  extension (id: ArticleId) def value: Long = id

  /** Newest last. Only a tie-break: the identifier's own clock is not `Clock[F]`, so `createdAt` stays
    * the field that decides when an article was created.
    */
  given Ordering[ArticleId] = Ordering.by(_.value)

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
