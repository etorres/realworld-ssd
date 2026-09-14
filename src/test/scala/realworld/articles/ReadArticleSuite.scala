package realworld.articles

import cats.effect.IO
import cats.syntax.all.*

import realworld.RequirementSuite
import realworld.articles.entity.ArticleError

import ArticlesFixture.*

/** R4: Read an article. */
class ReadArticleSuite extends RequirementSuite[ArticlesFixture]:

  protected def setting: IO[ArticlesFixture] = ArticlesFixture()

  requirements(
    (
      "R4.1",
      "returns the article with its author, tags, favorites count, and both times",
      fixture =>
        for
          celeb <- fixture.register("celeb", "celeb@jake.jake").map(_.user.id)
          jake <- fixture.register("jake", "jake@jake.jake").map(_.user.id)
          published <- fixture.publish(
            celeb,
            "Dragons",
            "Ever wonder how?",
            "It takes a Jacobian",
            List("dragons").some
          )
          _ <- accepted(fixture.articles.favoriteArticle(jake, "dragons"))
          view <- accepted(fixture.articles.readArticle(caller = None, "dragons"))
        yield
          assertEquals(view.article.title, "Dragons")
          assertEquals(view.article.body, "It takes a Jacobian")
          assertEquals(view.article.tags, List("dragons"))
          assertEquals(view.author.username, "celeb")
          assertEquals(view.favoritesCount, 1)
          assertEquals(view.article.createdAt, published.article.createdAt)
          assertEquals(view.article.updatedAt, published.article.updatedAt)
    ),
    (
      "R4.2",
      "reports whether the caller favorited the article and follows its author",
      fixture =>
        for
          celeb <- fixture.register("celeb", "celeb@jake.jake").map(_.user.id)
          jake <- fixture.register("jake", "jake@jake.jake").map(_.user.id)
          _ <- fixture.publish(celeb, "Dragons")
          before <- accepted(fixture.articles.readArticle(jake.some, "dragons"))
          _ <- fixture.follow(jake, "celeb")
          _ <- accepted(fixture.articles.favoriteArticle(jake, "dragons"))
          after <- accepted(fixture.articles.readArticle(jake.some, "dragons"))
          anonymous <- accepted(fixture.articles.readArticle(caller = None, "dragons"))
        yield
          assertEquals((before.favorited, before.author.following), (false, false))
          assertEquals((after.favorited, after.author.following), (true, true))
          assertEquals(anonymous.favorited, false, "an anonymous reader favorited nothing")
    ),
    (
      "R4.3",
      "rejects an unknown slug as not found",
      fixture =>
        for
          celeb <- fixture.register("celeb", "celeb@jake.jake").map(_.user.id)
          _ <- fixture.publish(celeb, "Dragons")
          error <- rejected(fixture.articles.readArticle(caller = None, "no-such-article"))
        yield assertEquals(error, ArticleError.NoSuchArticle)
    )
  )
