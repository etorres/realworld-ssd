package realworld.comments

import cats.effect.IO
import cats.syntax.all.*

import realworld.RequirementSuite
import realworld.support.TestDatabase
import realworld.comments.entity.CommentError

import CommentsFixture.*

/** R2: List comments. */
class ListCommentsSuite extends RequirementSuite[CommentsFixture]:

  protected def setting: IO[CommentsFixture] = CommentsFixture()

  private def article(fixture: CommentsFixture) =
    for
      jake <- fixture.register("jake", "jake@jake.jake").map(_.user.id)
      _ <- fixture.publish(jake, "Dragons")
    yield jake

  requirements(
    (
      "R2.1",
      "returns every comment attached to the article, most recently created first",
      fixture =>
        for
          jake <- article(fixture)
          _ <- fixture.publish(jake, "Other")
          _ <- accepted(fixture.comments.addComment(jake, "dragons", "first"))
          _ <- accepted(fixture.comments.addComment(jake, "dragons", "second"))
          _ <- accepted(fixture.comments.addComment(jake, "other", "elsewhere"))
          views <- accepted(fixture.comments.listComments(None, "dragons"))
        yield assertEquals(
          views.map(_.comment.body),
          List("second", "first"),
          "wrong order, or a comment from another article leaked in"
        )
    ),
    (
      "R2.1",
      "keeps the newest first when comments share a creation instant and a slot is reused",
      // Deleting a comment frees a slot; a vacuum lets the next insert take it. Without a tie-break the
      // new comment then comes back from the middle of the table rather than the front.
      fixture =>
        for
          jake <- article(fixture)
          first <- accepted(fixture.comments.addComment(jake, "dragons", "first"))
          second <- accepted(fixture.comments.addComment(jake, "dragons", "second"))
          _ <- accepted(fixture.comments.addComment(jake, "dragons", "third"))
          _ <- TestDatabase.flatten("comments")
          _ <- accepted(fixture.comments.deleteComment(jake, "dragons", second.comment.id.value))
          _ <- TestDatabase.vacuum("comments")
          _ <- accepted(fixture.comments.addComment(jake, "dragons", "fourth"))
          _ <- TestDatabase.flatten("comments")
          views <- accepted(fixture.comments.listComments(None, "dragons"))
        yield
          assertEquals(first.comment.id.value < second.comment.id.value, true, "ids are not in sequence")
          assertEquals(
            views.map(_.comment.body),
            List("fourth", "third", "first"),
            "comments sharing a creation instant came back in storage order"
          )
    ),
    (
      "R2.2",
      "reports for each comment whether the caller follows its author",
      fixture =>
        for
          jake <- article(fixture)
          reader <- fixture.register("reader", "reader@jake.jake").map(_.user.id)
          _ <- accepted(fixture.comments.addComment(jake, "dragons", "by jake"))
          before <- accepted(fixture.comments.listComments(reader.some, "dragons"))
          _ <- fixture.follow(reader, "jake")
          after <- accepted(fixture.comments.listComments(reader.some, "dragons"))
          anonymous <- accepted(fixture.comments.listComments(None, "dragons"))
        yield
          assertEquals(before.map(_.author.following), List(false))
          assertEquals(after.map(_.author.following), List(true))
          assertEquals(anonymous.map(_.author.following), List(false), "an anonymous reader follows nobody")
    ),
    (
      "R2.3",
      "returns an empty list while the article carries no comments",
      fixture =>
        for
          _ <- article(fixture)
          views <- accepted(fixture.comments.listComments(None, "dragons"))
        yield assertEquals(views, Nil)
    ),
    (
      "R2.4",
      "rejects an unknown article slug as not found",
      fixture =>
        for
          _ <- article(fixture)
          error <- rejected(fixture.comments.listComments(None, "no-such-article"))
        yield assertEquals(error, CommentError.NoSuchArticle)
    )
  )
