package realworld.articles.boundary

import cats.effect.kernel.{Async, Resource, Sync}
import cats.mtl.Raise
import cats.syntax.all.*

import realworld.articles.control.*
import realworld.articles.entity.ArticleError
import realworld.profiles.boundary.Profiles
import realworld.tags.boundary.Tags
import realworld.users.boundary.Users
import realworld.users.entity.UserId

/** The contract of the `articles` business component. Each method realises one `## Boundary` operation. */
trait Articles[F[_]]:
  def publishArticle(author: UserId, command: PublishArticle)(using Raise[F, ArticleError]): F[ArticleView]

  /** Neither listing operation can be rejected: an unregistered filter matches nothing (R2.9) and an
    * empty feed is an empty answer (R3.4).
    */
  def listArticles(caller: Option[UserId], filter: ArticleFilter, page: Page): F[ArticlePage]

  def readFeed(caller: UserId, page: Page): F[ArticlePage]

  def readArticle(caller: Option[UserId], slug: String)(using Raise[F, ArticleError]): F[ArticleView]

  def updateArticle(caller: UserId, slug: String, command: UpdateArticle)(using
      Raise[F, ArticleError]
  ): F[ArticleView]

  def deleteArticle(caller: UserId, slug: String)(using Raise[F, ArticleError]): F[Unit]

  def favoriteArticle(caller: UserId, slug: String)(using Raise[F, ArticleError]): F[ArticleView]

  def unfavoriteArticle(caller: UserId, slug: String)(using Raise[F, ArticleError]): F[ArticleView]

final case class PublishArticle(title: String, description: String, body: String, tags: Option[List[String]])

/** A partial update. An omitted field is unchanged (R5.1), and the tag list has three states of its own. */
final case class UpdateArticle(
    title: Option[String],
    description: Option[String],
    body: Option[String],
    tags: TagUpdate
)

object Articles:

  def apply[F[_]](service: ArticleService[F]): Articles[F] = new Articles[F]:

    def publishArticle(author: UserId, command: PublishArticle)(using
        Raise[F, ArticleError]
    ): F[ArticleView] =
      service.publish(author, command.title, command.description, command.body, command.tags)

    def listArticles(caller: Option[UserId], filter: ArticleFilter, page: Page): F[ArticlePage] =
      service.list(caller, filter, page)

    def readFeed(caller: UserId, page: Page): F[ArticlePage] = service.feed(caller, page)

    def readArticle(caller: Option[UserId], slug: String)(using Raise[F, ArticleError]): F[ArticleView] =
      service.read(caller, slug)

    def updateArticle(caller: UserId, slug: String, command: UpdateArticle)(using
        Raise[F, ArticleError]
    ): F[ArticleView] =
      service.update(caller, slug, command.title, command.description, command.body, command.tags)

    def deleteArticle(caller: UserId, slug: String)(using Raise[F, ArticleError]): F[Unit] =
      service.delete(caller, slug)

    def favoriteArticle(caller: UserId, slug: String)(using Raise[F, ArticleError]): F[ArticleView] =
      service.favorite(caller, slug)

    def unfavoriteArticle(caller: UserId, slug: String)(using Raise[F, ArticleError]): F[ArticleView] =
      service.unfavorite(caller, slug)

  /** The whole component, assembled over in-memory storage (decision D3). */
  def inMemory[F[_]: Sync](
      node: Int,
      accounts: Users[F],
      profiles: Profiles[F],
      tags: Tags[F]
  ): F[Articles[F]] =
    for
      articles <- ArticleRepository.inMemory[F]
      favorites <- FavoriteRepository.inMemory[F]
      component <- over(node, articles, favorites, accounts, profiles, tags)
    yield component

  /** The whole component, assembled over PostgreSQL (decision D11). */
  def postgres[F[_]: Async](
      pool: Resource[F, skunk.Session[F]],
      node: Int,
      accounts: Users[F],
      profiles: Profiles[F],
      tags: Tags[F]
  ): F[Articles[F]] =
    over(
      node,
      ArticleRepository.postgres(pool),
      FavoriteRepository.postgres(pool),
      accounts,
      profiles,
      tags
    )

  /** Everything above the repositories, which is the same either way. */
  private def over[F[_]: Sync](
      node: Int,
      articles: ArticleRepository[F],
      favorites: FavoriteRepository[F],
      accounts: Users[F],
      profiles: Profiles[F],
      tags: Tags[F]
  ): F[Articles[F]] =
    ArticleIds
      .tsid[F](node)
      .map(ids => apply(ArticleService(articles, favorites, ids, accounts, profiles, tags)))
