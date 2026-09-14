package realworld.articles.boundary

import java.time.Instant

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.{Codec, Decoder, HCursor}

import realworld.articles.control.{ArticlePage, ArticleView, TagUpdate}
import realworld.profiles.entity.Profile
import realworld.support.http.Timestamps.given

/** The author of an article, rendered exactly as `profiles` renders a profile. */
final case class AuthorPayload(
    username: String,
    bio: Option[String],
    image: Option[String],
    following: Boolean
)

object AuthorPayload:
  given Codec.AsObject[AuthorPayload] = KindlingsCodecAsObject.derived

  def from(profile: Profile): AuthorPayload =
    AuthorPayload(profile.username, profile.bio, profile.image, profile.following)

/** One article on its own, body included. */
final case class ArticlePayload(
    slug: String,
    title: String,
    description: String,
    body: String,
    tagList: List[String],
    createdAt: Instant,
    updatedAt: Instant,
    favorited: Boolean,
    favoritesCount: Int,
    author: AuthorPayload
)

object ArticlePayload:
  given Codec.AsObject[ArticlePayload] = KindlingsCodecAsObject.derived

  def from(view: ArticleView): ArticlePayload =
    ArticlePayload(
      slug = view.article.slug.value,
      title = view.article.title,
      description = view.article.description,
      body = view.article.body,
      tagList = view.article.tags,
      createdAt = view.article.createdAt,
      updatedAt = view.article.updatedAt,
      favorited = view.favorited,
      favoritesCount = view.favoritesCount,
      author = AuthorPayload.from(view.author)
    )

final case class ArticleBody(article: ArticlePayload)

object ArticleBody:
  given Codec.AsObject[ArticleBody] = KindlingsCodecAsObject.derived

  def from(view: ArticleView): ArticleBody = ArticleBody(ArticlePayload.from(view))

/** One article within a listing. R2.10 and R3.6 keep the body out: a listing is for browsing. */
final case class ArticleSummaryPayload(
    slug: String,
    title: String,
    description: String,
    tagList: List[String],
    createdAt: Instant,
    updatedAt: Instant,
    favorited: Boolean,
    favoritesCount: Int,
    author: AuthorPayload
)

object ArticleSummaryPayload:
  given Codec.AsObject[ArticleSummaryPayload] = KindlingsCodecAsObject.derived

  def from(view: ArticleView): ArticleSummaryPayload =
    ArticleSummaryPayload(
      slug = view.article.slug.value,
      title = view.article.title,
      description = view.article.description,
      tagList = view.article.tags,
      createdAt = view.article.createdAt,
      updatedAt = view.article.updatedAt,
      favorited = view.favorited,
      favoritesCount = view.favoritesCount,
      author = AuthorPayload.from(view.author)
    )

final case class ArticlesBody(articles: List[ArticleSummaryPayload], articlesCount: Int)

object ArticlesBody:
  given Codec.AsObject[ArticlesBody] = KindlingsCodecAsObject.derived

  def from(page: ArticlePage): ArticlesBody =
    ArticlesBody(page.articles.map(ArticleSummaryPayload.from), page.total)

/** Request fields stay optional so that a missing one becomes "can't be blank" from the domain (R1.6). */
final case class PublishPayload(
    title: Option[String],
    description: Option[String],
    body: Option[String],
    tagList: Option[List[String]]
)

final case class PublishRequest(article: PublishPayload)

object PublishRequest:
  given Decoder[PublishRequest] = hearth.kindlings.circederivation.KindlingsDecoder.derived

final case class UpdatePayload(
    title: Option[String],
    description: Option[String],
    body: Option[String],
    tags: TagUpdate
)

object UpdatePayload:
  /** Hand-written, because the three states of `tagList` are exactly what a derived `Option` decoder
    * collapses: it cannot tell an absent field from an explicit null (R5.8 against R5.10).
    */
  given Decoder[UpdatePayload] = (cursor: HCursor) =>
    for
      title <- cursor.get[Option[String]]("title")
      description <- cursor.get[Option[String]]("description")
      body <- cursor.get[Option[String]]("body")
      tags <- tagList(cursor)
    yield UpdatePayload(title, description, body, tags)

  private def tagList(cursor: HCursor): Decoder.Result[TagUpdate] =
    val at = cursor.downField("tagList")
    if !at.succeeded then Right(TagUpdate.Unchanged)
    else at.as[Option[List[String]]].map(_.fold(TagUpdate.WithoutValue)(TagUpdate.Replace(_)))

final case class UpdateRequest(article: UpdatePayload)

object UpdateRequest:
  given Decoder[UpdateRequest] = hearth.kindlings.circederivation.KindlingsDecoder.derived
