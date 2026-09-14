package realworld.articles.control

import cats.MonadThrow
import cats.data.NonEmptyChain
import cats.effect.kernel.Clock
import cats.effect.std.UUIDGen
import cats.mtl.Raise
import cats.mtl.syntax.all.*
import cats.syntax.all.*

import realworld.articles.entity.*
import realworld.profiles.boundary.Profiles
import realworld.profiles.entity.Profile
import realworld.tags.boundary.Tags
import realworld.users.boundary.Users
import realworld.users.entity.UserId

/** One article as a particular caller sees it: the post, its author rendered by `profiles`, and this
  * caller's relationship to it.
  */
final case class ArticleView(article: Article, author: Profile, favorited: Boolean, favoritesCount: Int)

/** A page of articles together with the total number matching the request, which R2.6 and R3.2 keep
  * independent of how many the page holds.
  */
final case class ArticlePage(articles: List[ArticleView], total: Int)

/** How many articles to return and where to start. */
final case class Page(limit: Int, offset: Int)

object Page:
  /** R2.7, R3.3 */
  val DefaultLimit = 20

  def of(limit: Option[Int], offset: Option[Int]): Page =
    Page(limit.filter(_ >= 0).getOrElse(DefaultLimit), offset.filter(_ >= 0).getOrElse(0))

/** The narrowing R2.2 to R2.5 allow. An absent filter narrows nothing. */
final case class ArticleFilter(tag: Option[String], author: Option[String], favoritedBy: Option[String])

object ArticleFilter:
  val None: ArticleFilter = ArticleFilter(scala.None, scala.None, scala.None)

/** What an update says about an article's tags. The three cases mean different things: R5.8 preserves,
  * R5.9 clears, and R5.10 rejects.
  */
enum TagUpdate:
  case Unchanged
  case Replace(tags: List[String])
  case WithoutValue

/** The `articles` use cases. */
final class ArticleService[F[_]: MonadThrow: UUIDGen: Clock](
    articles: ArticleRepository[F],
    favorites: FavoriteRepository[F],
    accounts: Users[F],
    profiles: Profiles[F],
    tags: Tags[F]
):

  def publish(
      author: UserId,
      title: String,
      description: String,
      body: String,
      declared: Option[List[String]]
  )(using
      Raise[F, ArticleError]
  ): F[ArticleView] =
    for
      _ <- rejectBlank(List("title" -> title, "description" -> description, "body" -> body))
      now <- Clock[F].realTimeInstant
      id <- UUIDGen[F].randomUUID.map(ArticleId.apply)
      slug <- available(Slug.from(title))
      attached = normalised(declared.getOrElse(Nil))
      article = Article(id, slug, title, description, body, attached, author, now, now)
      _ <- articles.save(article)
      _ <- tags.registerTags(attached)
      view <- viewOf(article, author.some)
    yield view

  def list(caller: Option[UserId], filter: ArticleFilter, page: Page): F[ArticlePage] =
    for
      stored <- articles.all
      byAuthor <- filter.author.traverse(identify)
      byFavoriter <- filter.favoritedBy.traverse(identify)
      marks <- favorites.all
      // R2.9 — a filter naming nobody matches nothing, rather than being ignored.
      matching =
        if byAuthor.contains(Option.empty[UserId]) || byFavoriter.contains(Option.empty[UserId]) then Nil
        else
          stored
            .filter(article => filter.tag.forall(article.tags.contains))
            .filter(article => byAuthor.flatten.forall(_ == article.author))
            .filter(article => byFavoriter.flatten.forall(user => marks(Favorite(user, article.id))))
      view <- paged(matching, caller, page)
    yield view

  def feed(caller: UserId, page: Page): F[ArticlePage] =
    for
      stored <- articles.all
      followed <- stored.map(_.author).distinct.filterA(follows(caller, _))
      view <- paged(stored.filter(article => followed.contains(article.author)), caller.some, page)
    yield view

  def read(caller: Option[UserId], slug: String)(using Raise[F, ArticleError]): F[ArticleView] =
    existing(slug).flatMap(viewOf(_, caller))

  def update(
      caller: UserId,
      slug: String,
      title: Option[String],
      description: Option[String],
      body: Option[String],
      declared: TagUpdate
  )(using Raise[F, ArticleError]): F[ArticleView] =
    for
      article <- authored(caller, slug)
      _ <- rejectBlank(
        List("title" -> title, "description" -> description, "body" -> body).collect {
          case (name, Some(value)) =>
            name -> value
        }
      )
      _ <- ArticleError.TagListWithoutValue.raise[F, Unit].whenA(declared == TagUpdate.WithoutValue)
      now <- Clock[F].realTimeInstant
      renamed = title.filter(_ != article.title)
      slugged <- renamed.traverse(fresh => available(Slug.from(fresh)))
      attached = declared match
        case TagUpdate.Replace(replacement) => normalised(replacement)
        case _ => article.tags
      updated = article.copy(
        slug = slugged.getOrElse(article.slug),
        title = title.getOrElse(article.title),
        description = description.getOrElse(article.description),
        body = body.getOrElse(article.body),
        tags = attached,
        updatedAt = now
      )
      _ <- articles.save(updated)
      _ <- tags.registerTags(attached)
      view <- viewOf(updated, caller.some)
    yield view

  def delete(caller: UserId, slug: String)(using Raise[F, ArticleError]): F[Unit] =
    for
      article <- authored(caller, slug)
      _ <- favorites.removeAllOf(article.id)
      _ <- articles.delete(article.id)
    yield ()

  def favorite(caller: UserId, slug: String)(using Raise[F, ArticleError]): F[ArticleView] =
    mark(caller, slug)(favorites.add)

  def unfavorite(caller: UserId, slug: String)(using Raise[F, ArticleError]): F[ArticleView] =
    mark(caller, slug)(favorites.remove)

  private def mark(caller: UserId, slug: String)(record: Favorite => F[Unit])(using
      Raise[F, ArticleError]
  ): F[ArticleView] =
    for
      article <- existing(slug)
      _ <- record(Favorite(caller, article.id))
      view <- viewOf(article, caller.some)
    yield view

  /** Newest first (R2.1, R3.1). The sort is stable and the repository yields insertion order, so articles
    * created within the same clock tick still come back newest first.
    */
  private def paged(matching: List[Article], caller: Option[UserId], page: Page): F[ArticlePage] =
    val ordered = matching.sortBy(_.createdAt.toEpochMilli).reverse
    ordered
      .slice(page.offset, page.offset + page.limit)
      .traverse(viewOf(_, caller))
      .map(ArticlePage(_, ordered.size))

  private def viewOf(article: Article, caller: Option[UserId]): F[ArticleView] =
    for
      // An article always references a real account, so absence here is a broken invariant rather than a
      // rejection the caller could act on. `liftTo` is ambiguous between cats and cats-mtl syntax here.
      author <- profiles
        .viewAuthor(article.author, caller)
        .flatMap:
          case Some(profile) => profile.pure[F]
          case None =>
            MonadThrow[F].raiseError[Profile](
              IllegalStateException(
                s"article ${article.slug.value} references an account that does not exist"
              )
            )
      marks <- favorites.all
    yield ArticleView(
      article = article,
      author = author,
      favorited = caller.exists(user => marks(Favorite(user, article.id))),
      favoritesCount = marks.count(_.article == article.id)
    )

  private def follows(caller: UserId, author: UserId): F[Boolean] =
    profiles.viewAuthor(author, caller.some).map(_.exists(_.following))

  private def identify(username: String): F[Option[UserId]] =
    accounts.describeUser(username).map(_.map(_.id))

  private def existing(slug: String)(using Raise[F, ArticleError]): F[Article] =
    articles
      .findBySlug(Slug.of(slug))
      .flatMap(_.fold(ArticleError.NoSuchArticle.raise[F, Article])(_.pure[F]))

  /** An article the caller may change. Absence outranks ownership, so a stranger probing an unknown slug
    * learns only that it is unknown.
    */
  private def authored(caller: UserId, slug: String)(using Raise[F, ArticleError]): F[Article] =
    existing(slug).flatTap(article =>
      ArticleError.NotTheAuthor.raise[F, Unit].whenA(article.author != caller)
    )

  /** R1.5 — a title whose slug is taken still gets published, under a distinct one. */
  private def available(base: Slug): F[Slug] =
    def attempt(n: Int): F[Slug] =
      val candidate = if n == 1 then base else Slug.numbered(base, n)
      articles.findBySlug(candidate).flatMap(_.fold(candidate.pure[F])(_ => attempt(n + 1)))
    attempt(1)

  /** R1.2, R5.7 — each distinct tag, in the order given. Unusable names are dropped, as `tags` R1.3 drops
    * them from the registry.
    */
  private def normalised(declared: List[String]): List[String] =
    declared.map(_.trim).filter(_.nonEmpty).distinct

  private def rejectBlank(fields: List[(String, String)])(using Raise[F, ArticleError]): F[Unit] =
    NonEmptyChain
      .fromSeq(fields.collect { case (name, value) if value.trim.isEmpty => name })
      .traverse_(ArticleError.Blank(_).raise[F, Unit])
