package realworld.comments

import cats.effect.IO
import cats.mtl.Raise
import io.circe.parser
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{HttpApp, Method, Request, Response, Uri}

import realworld.Rejections
import realworld.articles.boundary.{Articles, ArticlesRoutes, PublishArticle}
import realworld.articles.control.ArticleView
import realworld.articles.entity.ArticleError
import realworld.articles.ArticlesFixture.TestNode
import realworld.articles.control.{ArticleRepository, FavoriteRepository}
import realworld.comments.boundary.{Comments, CommentsRoutes}
import realworld.comments.control.CommentRepository
import realworld.comments.entity.CommentError
import realworld.profiles.boundary.{Profiles, ProfilesRoutes}
import realworld.profiles.control.FollowRepository
import realworld.support.TestDatabase
import realworld.support.http.Api
import realworld.tags.boundary.{Tags, TagsRoutes}
import realworld.tags.control.TagRegistry
import realworld.users.UsersFixture
import realworld.users.boundary.{CallerAuth, RegisterUser, Users, UsersRoutes}
import realworld.users.control.UserRepository
import realworld.users.control.Session
import realworld.users.entity.{AuthToken, UserError, UserId}

/** Everything the `comments` requirement suites arrange.
  *
  * The components `comments` is declared to call are assembled for real: an author is rendered by
  * `profiles`, and an article's existence is `articles`' answer to give.
  */
final case class CommentsFixture(
    users: Users[IO],
    profiles: Profiles[IO],
    articles: Articles[IO],
    comments: Comments[IO],
    api: HttpApp[IO]
)

object CommentsFixture:

  def apply(): IO[CommentsFixture] =
    for
      pool <- TestDatabase.fresh(
        UserRepository.Tables ++ FollowRepository.Tables ++ TagRegistry.Tables ++
          ArticleRepository.Tables ++ FavoriteRepository.Tables ++ CommentRepository.Tables
      )
      users <- Users.postgres[IO](
        pool,
        UsersFixture.JwtSecret,
        UsersFixture.TokenTtl,
        UsersFixture.BcryptLogRounds
      )
      profiles = Profiles.postgres[IO](users, pool)
      tags = Tags.postgres[IO](pool)
      articles <- Articles.postgres[IO](pool, TestNode, users, profiles, tags)
      comments = Comments.postgres[IO](pool, articles, profiles)
    yield
      val caller = CallerAuth(users)
      val api = Api(
        UsersRoutes(users, caller).routes,
        ProfilesRoutes(profiles, caller).routes,
        ArticlesRoutes(articles, caller).routes,
        CommentsRoutes(comments, caller).routes,
        TagsRoutes(tags).routes
      )
      CommentsFixture(users, profiles, articles, comments, api)

  def accepted[A](operation: Raise[IO, CommentError] ?=> IO[A]): IO[A] =
    Rejections.accepted[CommentError, A](operation)

  def rejected[A](operation: Raise[IO, CommentError] ?=> IO[A]): IO[CommentError] =
    Rejections.rejected[CommentError, A](operation)

  extension (fixture: CommentsFixture)
    def register(username: String, email: String): IO[Session] =
      Rejections.accepted[UserError, Session](
        fixture.users.registerUser(RegisterUser(username, email, "password123"))
      )

    def publish(author: UserId, title: String): IO[ArticleView] =
      Rejections.accepted[ArticleError, ArticleView](
        fixture.articles.publishArticle(author, PublishArticle(title, "a description", "a body", None))
      )

    def follow(follower: UserId, username: String): IO[Unit] =
      Rejections
        .accepted[realworld.profiles.entity.ProfileError, realworld.profiles.entity.Profile](
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
