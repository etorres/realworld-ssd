package realworld.articles

import cats.data.NonEmptyChain
import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Status}

import realworld.{Rejections, RequirementSuite}
import realworld.articles.boundary.UpdateArticle
import realworld.articles.control.TagUpdate
import realworld.articles.entity.ArticleError

import ArticlesFixture.*

/** R5: Update an article. */
class UpdateArticleSuite extends RequirementSuite[ArticlesFixture]:

  protected def setting: IO[ArticlesFixture] = ArticlesFixture()

  private val nothing = UpdateArticle(None, None, None, TagUpdate.Unchanged)

  /** One author with one tagged article. */
  private def owned(fixture: ArticlesFixture) =
    for
      jake <- fixture.register("jake", "jake@jake.jake").map(_.user.id)
      article <- fixture.publish(
        jake,
        "Dragons",
        "Ever wonder how?",
        "It takes a Jacobian",
        List("dragons", "training").some
      )
    yield (jake, article)

  requirements(
    (
      "R5.1",
      "applies exactly the submitted fields, leaves the rest unchanged, and advances the update time",
      // Arranges its own fixture, because "advances" is only observable across two clock reads. It used
      // to jump a second forward under TestControl, on the belief that the clock had only millisecond
      // resolution; it has microsecond resolution, and a round trip to the database is far wider than
      // that. Virtual time cannot drive a real socket anyway (decision D11, hazard H2).
      _ =>
        for
          fixture <- ArticlesFixture()
          (jake, published) <- owned(fixture)
          updated <- accepted(
            fixture.articles.updateArticle(jake, "dragons", nothing.copy(body = "Updated body".some))
          )
        yield
          assertEquals(updated.article.body, "Updated body")
          assertEquals(updated.article.title, "Dragons", "an omitted field changed")
          assertEquals(updated.article.description, "Ever wonder how?")
          assertEquals(updated.article.tags, List("dragons", "training"))
          assertEquals(updated.article.createdAt, published.article.createdAt)
          assert(
            updated.article.updatedAt.isAfter(published.article.updatedAt),
            s"the update time did not advance: ${updated.article.updatedAt}"
          )
    ),
    (
      "R5.2",
      "derives a new slug when the title changes and serves the article under it thereafter",
      fixture =>
        for
          (jake, _) <- owned(fixture)
          updated <- accepted(
            fixture.articles.updateArticle(jake, "dragons", nothing.copy(title = "How to train yours".some))
          )
          fetched <- accepted(fixture.articles.readArticle(None, "how-to-train-yours"))
          stale <- rejected(fixture.articles.readArticle(None, "dragons"))
        yield
          assertEquals(updated.article.slug.value, "how-to-train-yours")
          assertEquals(fetched.article.title, "How to train yours")
          assertEquals(stale, ArticleError.NoSuchArticle, "the article is still served under its old slug")
    ),
    (
      "R5.3",
      "rejects submitted blank fields as a validation failure naming every one of them",
      fixture =>
        for
          (jake, _) <- owned(fixture)
          error <- rejected(
            fixture.articles
              .updateArticle(jake, "dragons", UpdateArticle("".some, "  ".some, None, TagUpdate.Unchanged))
          )
        yield assertEquals(error, ArticleError.Blank(NonEmptyChain("title", "description")))
    ),
    (
      "R5.4",
      "rejects an authenticated caller who is not the author as forbidden",
      fixture =>
        for
          (_, _) <- owned(fixture)
          rival <- fixture.register("rival", "rival@jake.jake").map(_.user.id)
          error <- rejected(
            fixture.articles.updateArticle(rival, "dragons", nothing.copy(body = "hijacked".some))
          )
        yield assertEquals(error, ArticleError.NotTheAuthor)
    ),
    (
      "R5.5",
      "rejects an unknown slug as not found",
      fixture =>
        for
          (jake, _) <- owned(fixture)
          error <- rejected(fixture.articles.updateArticle(jake, "no-such-article", nothing))
        yield assertEquals(error, ArticleError.NoSuchArticle)
    ),
    (
      "R5.6",
      "rejects a request carrying no authentication as unauthorized",
      fixture =>
        for
          (_, _) <- owned(fixture)
          response <- fixture.call(Method.PUT, "/api/articles/dragons", """{"article":{"body":"x"}}""".some)
          body <- response.as[Json]
        yield
          assertEquals(response.status, Status.Unauthorized)
          assertEquals(
            body.hcursor.downField("errors").downField("token").as[List[String]],
            Right(List("is missing"))
          )
    ),
    (
      "R5.7",
      "replaces the tags with exactly the submitted list, in order, registering any newly in use",
      fixture =>
        for
          (jake, _) <- owned(fixture)
          updated <- accepted(
            fixture.articles
              .updateArticle(
                jake,
                "dragons",
                nothing.copy(tags = TagUpdate.Replace(List("reactjs", "angularjs")))
              )
          )
          inUse <- fixture.tags.listTags.map(_.map(_.value))
        yield
          assertEquals(updated.article.tags, List("reactjs", "angularjs"))
          assert(inUse.toSet.contains("reactjs"), s"a newly used tag was not registered: $inUse")
          assert(inUse.toSet.contains("angularjs"), s"a newly used tag was not registered: $inUse")
    ),
    (
      "R5.8",
      "leaves the tags unchanged when no tag list is submitted",
      fixture =>
        for
          (jake, _) <- owned(fixture)
          updated <- accepted(
            fixture.articles.updateArticle(jake, "dragons", nothing.copy(body = "Untouched tags".some))
          )
        yield assertEquals(updated.article.tags, List("dragons", "training"))
    ),
    (
      "R5.9",
      "removes every tag when an empty tag list is submitted",
      fixture =>
        for
          (jake, _) <- owned(fixture)
          updated <- accepted(
            fixture.articles.updateArticle(jake, "dragons", nothing.copy(tags = TagUpdate.Replace(Nil)))
          )
          fetched <- accepted(fixture.articles.readArticle(None, "dragons"))
        yield
          assertEquals(updated.article.tags, Nil)
          assertEquals(fetched.article.tags, Nil, "the tags came back")
    ),
    (
      "R5.10",
      "rejects a tag list submitted with no value, as a validation failure naming the tag list",
      fixture =>
        for
          (jake, _) <- owned(fixture)
          error <- rejected(
            fixture.articles.updateArticle(jake, "dragons", nothing.copy(tags = TagUpdate.WithoutValue))
          )
          // Must be the author's own token: a stranger is refused for ownership before tags are looked at.
          session <- Rejections.accepted[realworld.users.entity.UserError, realworld.users.control.Session](
            fixture.users.getCurrentUser(jake)
          )
          over <- fixture.call(
            Method.PUT,
            "/api/articles/dragons",
            """{"article":{"tagList":null}}""".some,
            session.token.some
          )
          body <- over.as[Json]
        yield
          assertEquals(error, ArticleError.TagListWithoutValue)
          // Over the wire a null tag list must reach the same rejection, not a decoding failure.
          assertEquals(over.status, Status.UnprocessableContent, s"a null tag list was not rejected: $body")
    )
  )
