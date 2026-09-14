package realworld.articles

import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Status}

import realworld.{Rejections, RequirementSuite}
import realworld.articles.control.Page

import ArticlesFixture.*

/** R3: Read the feed. */
class ReadFeedSuite extends RequirementSuite[ArticlesFixture]:

  protected def setting: IO[ArticlesFixture] = ArticlesFixture()

  /** Jake follows celeb, who has written two articles. A third author writes one jake should never see. */
  private def followed(fixture: ArticlesFixture) =
    for
      jake <- fixture.register("jake", "jake@jake.jake").map(_.user.id)
      celeb <- fixture.register("celeb", "celeb@jake.jake").map(_.user.id)
      stranger <- fixture.register("stranger", "stranger@jake.jake").map(_.user.id)
      _ <- fixture.publish(celeb, "Celeb One")
      _ <- fixture.publish(stranger, "Stranger One")
      _ <- fixture.publish(celeb, "Celeb Two")
      _ <- fixture.follow(jake, "celeb")
    yield jake

  private def slugs(page: realworld.articles.control.ArticlePage) =
    page.articles.map(_.article.slug.value)

  requirements(
    (
      "R3.1",
      "returns only articles by the users the caller follows, newest first, with the total",
      fixture =>
        for
          jake <- followed(fixture)
          page <- fixture.articles.readFeed(jake, Page(Page.DefaultLimit, 0))
        yield
          assertEquals(slugs(page), List("celeb-two", "celeb-one"))
          assertEquals(page.total, 2)
    ),
    (
      "R3.2",
      "returns at most the limit from the offset, while the total still counts the whole feed",
      fixture =>
        for
          jake <- followed(fixture)
          first <- fixture.articles.readFeed(jake, Page(limit = 1, offset = 0))
          second <- fixture.articles.readFeed(jake, Page(limit = 1, offset = 1))
        yield
          assertEquals(slugs(first), List("celeb-two"))
          assertEquals(slugs(second), List("celeb-one"))
          assertEquals(first.total, 2, "the total collapsed to the page size")
    ),
    (
      "R3.3",
      "returns at most 20 feed articles from the first one when no limit or offset is supplied",
      fixture =>
        for
          jake <- fixture.register("jake", "jake@jake.jake").map(_.user.id)
          celeb <- fixture.register("celeb", "celeb@jake.jake").map(_.user.id)
          _ <- (1 to 23).toList.traverse_(n => fixture.publish(celeb, s"Celeb $n"))
          _ <- fixture.follow(jake, "celeb")
          page <- fixture.articles.readFeed(jake, Page.of(None, None))
        yield
          assertEquals(page.articles.size, 20)
          assertEquals(page.total, 23)
          assertEquals(page.articles.head.article.title, "Celeb 23")
    ),
    (
      "R3.4",
      "returns no articles and a total of zero while the caller follows nobody",
      fixture =>
        for
          jake <- fixture.register("jake", "jake@jake.jake").map(_.user.id)
          celeb <- fixture.register("celeb", "celeb@jake.jake").map(_.user.id)
          _ <- fixture.publish(celeb, "Unseen")
          page <- fixture.articles.readFeed(jake, Page.of(None, None))
        yield
          assertEquals(page.articles, Nil)
          assertEquals(page.total, 0)
    ),
    (
      "R3.5",
      "rejects a request carrying no authentication as unauthorized",
      fixture =>
        for
          response <- fixture.call(Method.GET, "/api/articles/feed")
          body <- response.as[Json]
        yield
          assertEquals(response.status, Status.Unauthorized)
          assertEquals(
            body.hcursor.downField("errors").downField("token").as[List[String]],
            Right(List("is missing"))
          )
    ),
    (
      "R3.6",
      "omits each article's body from the feed listing",
      fixture =>
        for
          jake <- followed(fixture)
          session <- Rejections.accepted[realworld.users.entity.UserError, realworld.users.control.Session](
            fixture.users.getCurrentUser(jake)
          )
          response <- fixture.call(Method.GET, "/api/articles/feed", token = session.token.some)
          body <- response.as[Json]
          listed = body.hcursor.downField("articles").downN(0)
        yield
          assertEquals(response.status, Status.Ok)
          assertEquals(listed.downField("title").as[String].isRight, true, s"nothing was listed: $body")
          assertEquals(listed.downField("body").succeeded, false, s"the feed carried a body: $body")
    )
  )
