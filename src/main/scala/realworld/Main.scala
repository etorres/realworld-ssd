package realworld

import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all.*
import com.comcast.ip4s.{Host, Port}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server

import realworld.support.config.AppConfig
import realworld.support.http.Api
import realworld.support.storage.Database
import realworld.tags.control.TagRegistry
import realworld.tags.boundary.{Tags, TagsRoutes}
import realworld.articles.boundary.{Articles, ArticlesRoutes}
import realworld.comments.boundary.{Comments, CommentsRoutes}
import realworld.profiles.boundary.{Profiles, ProfilesRoutes}
import realworld.users.boundary.{CallerAuth, Users, UsersRoutes}

/** Application entry point and wiring. Declared in `AGENTS.md` as outside the spec surface: it decides
  * which implementations are assembled, never how the capabilities behave.
  */
object Main extends IOApp.Simple:

  def run: IO[Unit] = server.useForever

  private def server: Resource[IO, Server] =
    for
      config <- AppConfig.load.load[IO].toResource
      host <- parsed("REALWORLD_HOST", config.host)(Host.fromString).toResource
      port <- parsed("REALWORLD_PORT", config.port.toString)(Port.fromString).toResource
      pool <- Database.pool[IO](config.database)
      _ <- Database.migrate(pool, TagRegistry.Tables).toResource
      users <- Users.inMemory[IO](config.jwtSecret.value, config.tokenTtl, config.bcryptLogRounds).toResource
      profiles <- Profiles.inMemory[IO](users).toResource
      tags = Tags.postgres[IO](pool)
      articles <- Articles.inMemory[IO](users, profiles, tags).toResource
      comments <- Comments.inMemory[IO](articles, profiles).toResource
      caller = CallerAuth(users)
      api = Api(
        UsersRoutes(users, caller).routes,
        ProfilesRoutes(profiles, caller).routes,
        ArticlesRoutes(articles, caller).routes,
        CommentsRoutes(comments, caller).routes,
        TagsRoutes(tags).routes
      )
      server <- EmberServerBuilder.default[IO].withHost(host).withPort(port).withHttpApp(api).build
    yield server

  private def parsed[A](setting: String, raw: String)(read: String => Option[A]): IO[A] =
    read(raw).liftTo[IO](new IllegalArgumentException(s"$setting is not a valid value: $raw"))
