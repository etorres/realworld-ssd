package realworld.articles

import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Status}

import realworld.RequirementSuite
import realworld.articles.control.{ArticleFilter, Page}

import ArticlesFixture.*

/** R2: List articles. */
class ListArticlesSuite extends RequirementSuite[ArticlesFixture]:

  protected def setting: IO[ArticlesFixture] = ArticlesFixture()

  private val everything = ArticleFilter.None
  private val firstPage = Page(Page.DefaultLimit, 0)

  /** Two authors and three articles, published oldest first. */
  private def library(fixture: ArticlesFixture) =
    for
      jake <- fixture.register("jake", "jake@jake.jake").map(_.user.id)
      celeb <- fixture.register("celeb", "celeb@jake.jake").map(_.user.id)
      oldest <- fixture.publish(jake, "Oldest", tags = List("dragons").some)
      middle <- fixture.publish(celeb, "Middle", tags = List("training").some)
      newest <- fixture.publish(jake, "Newest", tags = List("dragons", "training").some)
    yield (jake, celeb, List(oldest, middle, newest).map(_.article.slug.value))

  private def slugs(page: realworld.articles.control.ArticlePage) =
    page.articles.map(_.article.slug.value)

  requirements(
    (
      "R2.1",
      "returns articles most recently created first, with the total matching",
      fixture =>
        for
          (_, _, published) <- library(fixture)
          page <- fixture.articles.listArticles(None, everything, firstPage)
        yield
          assertEquals(slugs(page), published.reverse)
          assertEquals(page.total, 3)
    ),
    (
      "R2.2",
      "returns only articles carrying the tag filter",
      fixture =>
        for
          (_, _, _) <- library(fixture)
          page <- fixture.articles.listArticles(None, everything.copy(tag = "dragons".some), firstPage)
        yield
          assertEquals(slugs(page), List("newest", "oldest"))
          assertEquals(page.total, 2)
    ),
    (
      "R2.3",
      "returns only articles written by the author filter",
      fixture =>
        for
          (_, _, _) <- library(fixture)
          page <- fixture.articles.listArticles(None, everything.copy(author = "celeb".some), firstPage)
        yield
          assertEquals(slugs(page), List("middle"))
          assertEquals(page.total, 1)
    ),
    (
      "R2.4",
      "returns only articles the favoriting-user filter has favorited",
      fixture =>
        for
          (_, celeb, _) <- library(fixture)
          _ <- accepted(fixture.articles.favoriteArticle(celeb, "oldest"))
          page <- fixture.articles.listArticles(None, everything.copy(favoritedBy = "celeb".some), firstPage)
        yield
          assertEquals(slugs(page), List("oldest"))
          assertEquals(page.total, 1)
    ),
    (
      "R2.5",
      "returns only articles satisfying every filter supplied together",
      fixture =>
        for
          (jake, _, _) <- library(fixture)
          _ <- accepted(fixture.articles.favoriteArticle(jake, "newest"))
          _ <- accepted(fixture.articles.favoriteArticle(jake, "oldest"))
          page <- fixture.articles.listArticles(
            None,
            ArticleFilter(tag = "training".some, author = "jake".some, favoritedBy = "jake".some),
            firstPage
          )
        yield
          // "newest" alone carries the tag, is jake's, and was favorited by jake.
          assertEquals(slugs(page), List("newest"))
          assertEquals(page.total, 1)
    ),
    (
      "R2.6",
      "returns at most the limit from the offset, while the total still counts every match",
      fixture =>
        for
          (_, _, _) <- library(fixture)
          first <- fixture.articles.listArticles(None, everything, Page(limit = 1, offset = 0))
          second <- fixture.articles.listArticles(None, everything, Page(limit = 1, offset = 1))
        yield
          assertEquals(slugs(first), List("newest"))
          assertEquals(slugs(second), List("middle"))
          assertEquals(first.total, 3, "the total collapsed to the page size")
          assertEquals(second.total, 3)
    ),
    (
      "R2.7",
      "returns at most 20 articles from the first one when no limit or offset is supplied",
      fixture =>
        for
          jake <- fixture.register("jake", "jake@jake.jake").map(_.user.id)
          _ <- (1 to 25).toList.traverse_(n => fixture.publish(jake, s"Article $n"))
          page <- fixture.articles.listArticles(None, everything, Page.of(None, None))
        yield
          assertEquals(page.articles.size, 20)
          assertEquals(page.total, 25)
          assertEquals(
            page.articles.head.article.title,
            "Article 25",
            "the first page did not start at the newest"
          )
    ),
    (
      "R2.8",
      "reports for each article whether the caller favorited it and follows its author",
      fixture =>
        for
          (jake, _, _) <- library(fixture)
          _ <- fixture.follow(jake, "celeb")
          _ <- accepted(fixture.articles.favoriteArticle(jake, "middle"))
          page <- fixture.articles.listArticles(jake.some, everything, firstPage)
          middle = page.articles.find(_.article.slug.value == "middle").getOrElse(fail("middle vanished"))
          newest = page.articles.find(_.article.slug.value == "newest").getOrElse(fail("newest vanished"))
        yield
          assertEquals(middle.favorited, true)
          assertEquals(middle.author.following, true, "jake follows celeb")
          assertEquals(newest.favorited, false)
          assertEquals(newest.author.following, false, "jake does not follow himself")
    ),
    (
      "R2.9",
      "returns no articles and a total of zero when a filter names an unregistered user",
      fixture =>
        for
          (_, _, _) <- library(fixture)
          byAuthor <- fixture.articles.listArticles(None, everything.copy(author = "nobody".some), firstPage)
          byFavoriter <- fixture.articles.listArticles(
            None,
            everything.copy(favoritedBy = "nobody".some),
            firstPage
          )
        yield
          assertEquals((slugs(byAuthor), byAuthor.total), (Nil, 0))
          assertEquals((slugs(byFavoriter), byFavoriter.total), (Nil, 0))
    ),
    (
      "R2.10",
      "omits each article's body from the listing",
      fixture =>
        for
          (_, _, _) <- library(fixture)
          response <- fixture.call(Method.GET, "/api/articles")
          body <- response.as[Json]
          listed = body.hcursor.downField("articles").downN(0)
        yield
          assertEquals(response.status, Status.Ok)
          assertEquals(listed.downField("title").as[String].isRight, true, s"nothing was listed: $body")
          assertEquals(listed.downField("body").succeeded, false, s"the listing carried a body: $body")
    )
  )
