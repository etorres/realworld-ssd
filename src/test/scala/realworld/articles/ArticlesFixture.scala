package realworld.articles

import cats.effect.IO
import cats.mtl.Raise
import io.circe.parser
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{HttpApp, Method, Request, Response, Uri}

import realworld.Rejections
import realworld.articles.boundary.{Articles, ArticlesRoutes, PublishArticle}
import realworld.articles.control.ArticleView
import realworld.articles.entity.ArticleError
import cats.syntax.all.*

import realworld.articles.control.{ArticleRepository, FavoriteRepository}
import realworld.profiles.boundary.{Profiles, ProfilesRoutes}
import realworld.profiles.control.FollowRepository
import realworld.profiles.entity.ProfileError
import realworld.support.TestDatabase
import realworld.support.http.Api
import realworld.tags.boundary.{Tags, TagsRoutes}
import realworld.tags.control.TagRegistry
import realworld.users.UsersFixture
import realworld.users.boundary.{CallerAuth, RegisterUser, Users, UsersRoutes}
import realworld.users.control.UserRepository
import realworld.users.control.Session
import realworld.users.entity.{AuthToken, UserError, UserId}

/** Everything the `articles` requirement suites arrange.
  *
  * The three components `articles` is declared to call are assembled for real rather than stubbed: an
  * author is rendered by `profiles`, which reads `users` — so a stub would test the wiring's shadow.
  */
final case class ArticlesFixture(
    users: Users[IO],
    profiles: Profiles[IO],
    tags: Tags[IO],
    articles: Articles[IO],
    api: HttpApp[IO]
)

object ArticlesFixture:

  def apply(): IO[ArticlesFixture] =
    for
      pool <- TestDatabase.fresh(
        UserRepository.Tables ++ FollowRepository.Tables ++ TagRegistry.Tables ++
          ArticleRepository.Tables ++ FavoriteRepository.Tables
      )
      users <- Users.postgres[IO](
        pool,
        UsersFixture.JwtSecret,
        UsersFixture.TokenTtl,
        UsersFixture.BcryptLogRounds
      )
      profiles = Profiles.postgres[IO](users, pool)
      tags = Tags.postgres[IO](pool)
      articles <- Articles.postgres[IO](pool, users, profiles, tags)
    yield
      val caller = CallerAuth(users)
      val api = Api(
        UsersRoutes(users, caller).routes,
        ProfilesRoutes(profiles, caller).routes,
        ArticlesRoutes(articles, caller).routes,
        TagsRoutes(tags).routes
      )
      ArticlesFixture(users, profiles, tags, articles, api)

  def accepted[A](operation: Raise[IO, ArticleError] ?=> IO[A]): IO[A] =
    Rejections.accepted[ArticleError, A](operation)

  def rejected[A](operation: Raise[IO, ArticleError] ?=> IO[A]): IO[ArticleError] =
    Rejections.rejected[ArticleError, A](operation)

  extension (fixture: ArticlesFixture)
    def register(username: String, email: String): IO[Session] =
      Rejections.accepted[UserError, Session](
        fixture.users.registerUser(RegisterUser(username, email, "password123"))
      )

    /** An author and one article of theirs — the arrangement most rows start from. */
    def publish(
        author: UserId,
        title: String,
        description: String = "a description",
        body: String = "a body",
        tags: Option[List[String]] = None
    ): IO[ArticleView] =
      accepted(fixture.articles.publishArticle(author, PublishArticle(title, description, body, tags)))

    /** Rows about the feed and the follow flag need a real relationship recorded by `profiles`. */
    def follow(follower: UserId, username: String): IO[Unit] =
      Rejections
        .accepted[ProfileError, realworld.profiles.entity.Profile](
          fixture.profiles.followUser(follower, username)
        )
        .void

    def call(
        method: Method,
        path: String,
        body: Option[String] = None,
        token: Option[AuthToken] = None
    ): IO[Response[IO]] =
      val base = Request[IO](method, Uri.unsafeFromString(path))
      val authorized = token.fold(base)(value => base.putHeaders("Authorization" -> s"Token ${value.value}"))
      fixture.api.run(body.fold(authorized)(raw => authorized.withEntity(json(raw))))

  private def json(raw: String) =
    parser.parse(raw).getOrElse(sys.error(s"the test wrote invalid JSON: $raw"))
