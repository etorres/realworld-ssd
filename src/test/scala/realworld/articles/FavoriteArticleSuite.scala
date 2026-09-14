package realworld.articles

import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Status}

import realworld.RequirementSuite
import realworld.articles.entity.ArticleError

import ArticlesFixture.*

/** R7: Favorite an article. */
class FavoriteArticleSuite extends RequirementSuite[ArticlesFixture]:

  protected def setting: IO[ArticlesFixture] = ArticlesFixture()

  private def readable(fixture: ArticlesFixture) =
    for
      celeb <- fixture.register("celeb", "celeb@jake.jake").map(_.user.id)
      jake <- fixture.register("jake", "jake@jake.jake").map(_.user.id)
      _ <- fixture.publish(celeb, "Dragons")
    yield jake

  requirements(
    (
      "R7.1",
      "records the favorite, raises the count by one, and returns the article marked as favorited",
      fixture =>
        for
          jake <- readable(fixture)
          view <- accepted(fixture.articles.favoriteArticle(jake, "dragons"))
          persisted <- accepted(fixture.articles.readArticle(jake.some, "dragons"))
        yield
          assertEquals((view.favorited, view.favoritesCount), (true, 1))
          assertEquals((persisted.favorited, persisted.favoritesCount), (true, 1))
    ),
    (
      "R7.2",
      "leaves the count unchanged when the caller has already favorited the article",
      fixture =>
        for
          jake <- readable(fixture)
          _ <- accepted(fixture.articles.favoriteArticle(jake, "dragons"))
          again <- accepted(fixture.articles.favoriteArticle(jake, "dragons"))
          // One unfavorite must undo two favorites, which it does only if the second recorded nothing.
          undone <- accepted(fixture.articles.unfavoriteArticle(jake, "dragons"))
        yield
          assertEquals((again.favorited, again.favoritesCount), (true, 1))
          assertEquals(undone.favoritesCount, 0)
    ),
    (
      "R7.3",
      "rejects an unknown slug as not found",
      fixture =>
        for
          jake <- readable(fixture)
          error <- rejected(fixture.articles.favoriteArticle(jake, "no-such-article"))
        yield assertEquals(error, ArticleError.NoSuchArticle)
    ),
    (
      "R7.4",
      "rejects a request carrying no authentication as unauthorized",
      fixture =>
        for
          _ <- readable(fixture)
          response <- fixture.call(Method.POST, "/api/articles/dragons/favorite")
          body <- response.as[Json]
        yield
          assertEquals(response.status, Status.Unauthorized)
          assertEquals(
            body.hcursor.downField("errors").downField("token").as[List[String]],
            Right(List("is missing"))
          )
    )
  )
