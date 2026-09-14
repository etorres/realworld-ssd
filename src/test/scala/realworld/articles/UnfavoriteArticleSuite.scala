package realworld.articles

import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Status}

import realworld.RequirementSuite
import realworld.articles.entity.ArticleError

import ArticlesFixture.*

/** R8: Unfavorite an article. */
class UnfavoriteArticleSuite extends RequirementSuite[ArticlesFixture]:

  protected def setting: IO[ArticlesFixture] = ArticlesFixture()

  private def readable(fixture: ArticlesFixture) =
    for
      celeb <- fixture.register("celeb", "celeb@jake.jake").map(_.user.id)
      jake <- fixture.register("jake", "jake@jake.jake").map(_.user.id)
      _ <- fixture.publish(celeb, "Dragons")
    yield jake

  requirements(
    (
      "R8.1",
      "removes the favorite, lowers the count by one, and returns the article marked as not favorited",
      fixture =>
        for
          jake <- readable(fixture)
          _ <- accepted(fixture.articles.favoriteArticle(jake, "dragons"))
          view <- accepted(fixture.articles.unfavoriteArticle(jake, "dragons"))
          persisted <- accepted(fixture.articles.readArticle(jake.some, "dragons"))
        yield
          assertEquals((view.favorited, view.favoritesCount), (false, 0))
          assertEquals((persisted.favorited, persisted.favoritesCount), (false, 0))
    ),
    (
      "R8.2",
      "leaves the count unchanged when the caller has not favorited the article",
      fixture =>
        for
          jake <- readable(fixture)
          fan <- fixture.register("fan", "fan@jake.jake").map(_.user.id)
          _ <- accepted(fixture.articles.favoriteArticle(fan, "dragons"))
          view <- accepted(fixture.articles.unfavoriteArticle(jake, "dragons"))
        yield
          assertEquals(view.favorited, false)
          assertEquals(view.favoritesCount, 1, "unfavoriting removed somebody else's favorite")
    ),
    (
      "R8.3",
      "rejects an unknown slug as not found",
      fixture =>
        for
          jake <- readable(fixture)
          error <- rejected(fixture.articles.unfavoriteArticle(jake, "no-such-article"))
        yield assertEquals(error, ArticleError.NoSuchArticle)
    ),
    (
      "R8.4",
      "rejects a request carrying no authentication as unauthorized",
      fixture =>
        for
          _ <- readable(fixture)
          response <- fixture.call(Method.DELETE, "/api/articles/dragons/favorite")
          body <- response.as[Json]
        yield
          assertEquals(response.status, Status.Unauthorized)
          assertEquals(
            body.hcursor.downField("errors").downField("token").as[List[String]],
            Right(List("is missing"))
          )
    )
  )
