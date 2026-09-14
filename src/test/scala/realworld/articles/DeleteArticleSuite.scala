package realworld.articles

import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Status}

import realworld.RequirementSuite
import realworld.articles.control.{ArticleFilter, Page}
import realworld.articles.entity.ArticleError

import ArticlesFixture.*

/** R6: Delete an article. */
class DeleteArticleSuite extends RequirementSuite[ArticlesFixture]:

  protected def setting: IO[ArticlesFixture] = ArticlesFixture()

  private def owned(fixture: ArticlesFixture) =
    for
      jake <- fixture.register("jake", "jake@jake.jake").map(_.user.id)
      _ <- fixture.publish(jake, "Dragons")
    yield jake

  requirements(
    (
      "R6.1",
      "removes the article with every favorite of it, and reports its slug as not found thereafter",
      fixture =>
        for
          jake <- owned(fixture)
          fan <- fixture.register("fan", "fan@jake.jake").map(_.user.id)
          _ <- accepted(fixture.articles.favoriteArticle(fan, "dragons"))
          _ <- accepted(fixture.articles.deleteArticle(jake, "dragons"))
          gone <- rejected(fixture.articles.readArticle(None, "dragons"))
          // A favorite that outlived its article would still count the deleted article as favorited.
          byFan <- fixture.articles.listArticles(
            None,
            ArticleFilter.None.copy(favoritedBy = "fan".some),
            Page.of(None, None)
          )
        yield
          assertEquals(gone, ArticleError.NoSuchArticle)
          assertEquals(byFan.total, 0, "a favorite outlived the article it marked")
    ),
    (
      "R6.2",
      "rejects an authenticated caller who is not the author as forbidden",
      fixture =>
        for
          _ <- owned(fixture)
          rival <- fixture.register("rival", "rival@jake.jake").map(_.user.id)
          error <- rejected(fixture.articles.deleteArticle(rival, "dragons"))
          survived <- accepted(fixture.articles.readArticle(None, "dragons"))
        yield
          assertEquals(error, ArticleError.NotTheAuthor)
          assertEquals(survived.article.title, "Dragons", "the refused delete took effect anyway")
    ),
    (
      "R6.3",
      "rejects an unknown slug as not found",
      fixture =>
        for
          jake <- owned(fixture)
          error <- rejected(fixture.articles.deleteArticle(jake, "no-such-article"))
        yield assertEquals(error, ArticleError.NoSuchArticle)
    ),
    (
      "R6.4",
      "rejects a request carrying no authentication as unauthorized",
      fixture =>
        for
          _ <- owned(fixture)
          response <- fixture.call(Method.DELETE, "/api/articles/dragons")
          body <- response.as[Json]
        yield
          assertEquals(response.status, Status.Unauthorized)
          assertEquals(
            body.hcursor.downField("errors").downField("token").as[List[String]],
            Right(List("is missing"))
          )
    )
  )
