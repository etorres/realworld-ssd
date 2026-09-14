package realworld.articles

import cats.data.NonEmptyChain
import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Status}

import realworld.RequirementSuite
import realworld.articles.boundary.PublishArticle
import realworld.articles.entity.ArticleError

import ArticlesFixture.*

/** R1: Publish an article. */
class PublishArticleSuite extends RequirementSuite[ArticlesFixture]:

  protected def setting: IO[ArticlesFixture] = ArticlesFixture()

  private def author(fixture: ArticlesFixture) = fixture.register("jake", "jake@jake.jake").map(_.user.id)

  requirements(
    (
      "R1.1",
      "creates the article with the caller as author, a slug from the title, and both times stamped",
      fixture =>
        for
          jake <- author(fixture)
          view <- fixture.publish(jake, "How to train your dragon", "Ever wonder how?", "It takes a Jacobian")
        yield
          assertEquals(view.article.title, "How to train your dragon")
          assertEquals(view.article.description, "Ever wonder how?")
          assertEquals(view.article.body, "It takes a Jacobian")
          assertEquals(view.article.slug.value, "how-to-train-your-dragon")
          assertEquals(view.article.author, jake)
          assertEquals(view.author.username, "jake")
          assertEquals(view.article.createdAt, view.article.updatedAt)
    ),
    (
      "R1.2",
      "attaches each distinct tag in the order given and registers them as in use",
      fixture =>
        for
          jake <- author(fixture)
          view <- fixture.publish(jake, "Dragons", tags = List("dragons", "training", "dragons").some)
          inUse <- fixture.tags.listTags.map(_.map(_.value))
        yield
          assertEquals(view.article.tags, List("dragons", "training"), "order or distinctness was lost")
          assertEquals(inUse.toSet, Set("dragons", "training"))
    ),
    (
      "R1.3",
      "creates the article with an empty tag list when none is submitted",
      fixture =>
        for
          jake <- author(fixture)
          view <- fixture.publish(jake, "Untagged")
        yield assertEquals(view.article.tags, Nil)
    ),
    (
      "R1.4",
      "reports the new article as not favorited, with a favorites count of zero",
      fixture =>
        for
          jake <- author(fixture)
          view <- fixture.publish(jake, "Fresh")
        yield
          assertEquals(view.favorited, false)
          assertEquals(view.favoritesCount, 0)
    ),
    (
      "R1.5",
      "derives a distinct slug rather than rejecting a title whose slug is taken",
      fixture =>
        for
          jake <- author(fixture)
          first <- fixture.publish(jake, "Duplicate Title")
          second <- fixture.publish(jake, "Duplicate Title")
          third <- fixture.publish(jake, "Duplicate Title")
        yield
          assertEquals(first.article.slug.value, "duplicate-title")
          assertNotEquals(second.article.slug.value, first.article.slug.value)
          assertNotEquals(third.article.slug.value, second.article.slug.value)
          assertNotEquals(third.article.slug.value, first.article.slug.value)
    ),
    (
      "R1.6",
      "rejects blank fields as a validation failure naming every one of them",
      fixture =>
        for
          jake <- author(fixture)
          error <- rejected(fixture.articles.publishArticle(jake, PublishArticle("", "  ", "", None)))
        yield assertEquals(error, ArticleError.Blank(NonEmptyChain("title", "description", "body")))
    ),
    (
      "R1.7",
      "rejects a request carrying no authentication as unauthorized",
      fixture =>
        for
          response <- fixture.call(
            Method.POST,
            "/api/articles",
            """{"article":{"title":"t","description":"d","body":"b"}}""".some
          )
          body <- response.as[Json]
        yield
          assertEquals(response.status, Status.Unauthorized)
          assertEquals(
            body.hcursor.downField("errors").downField("token").as[List[String]],
            Right(List("is missing"))
          )
    )
  )
