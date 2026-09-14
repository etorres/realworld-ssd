package realworld.comments

import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Status}

import realworld.RequirementSuite
import realworld.comments.entity.CommentError

import CommentsFixture.*

/** R1: Add a comment. */
class AddCommentSuite extends RequirementSuite[CommentsFixture]:

  protected def setting: IO[CommentsFixture] = CommentsFixture()

  /** One author with one article to reply to. */
  private def article(fixture: CommentsFixture) =
    for
      jake <- fixture.register("jake", "jake@jake.jake").map(_.user.id)
      _ <- fixture.publish(jake, "Dragons")
    yield jake

  requirements(
    (
      "R1.1",
      "creates the comment with the caller as author, both times stamped, and returns it",
      fixture =>
        for
          jake <- article(fixture)
          view <- accepted(fixture.comments.addComment(jake, "dragons", "It takes a Jacobian"))
        yield
          assertEquals(view.comment.body, "It takes a Jacobian")
          assertEquals(view.comment.author, jake)
          assertEquals(view.author.username, "jake")
          assertEquals(view.comment.createdAt, view.comment.updatedAt)
    ),
    (
      "R1.2",
      "gives each comment a whole-number identifier unique across every comment",
      fixture =>
        for
          jake <- article(fixture)
          first <- accepted(fixture.comments.addComment(jake, "dragons", "first"))
          second <- accepted(fixture.comments.addComment(jake, "dragons", "second"))
          _ <- accepted(fixture.comments.deleteComment(jake, "dragons", second.comment.id.value))
          third <- accepted(fixture.comments.addComment(jake, "dragons", "third"))
        yield
          assertNotEquals(first.comment.id.value, second.comment.id.value)
          // A withdrawn comment must not hand its identifier back to the next one.
          assertNotEquals(third.comment.id.value, second.comment.id.value)
          assertNotEquals(third.comment.id.value, first.comment.id.value)
    ),
    (
      "R1.3",
      "rejects a blank body as a validation failure naming the body field",
      fixture =>
        for
          jake <- article(fixture)
          error <- rejected(fixture.comments.addComment(jake, "dragons", "   "))
        yield assertEquals(error, CommentError.BlankBody)
    ),
    (
      "R1.4",
      "rejects an unknown article slug as not found",
      fixture =>
        for
          jake <- article(fixture)
          error <- rejected(fixture.comments.addComment(jake, "no-such-article", "orphan"))
        yield assertEquals(error, CommentError.NoSuchArticle)
    ),
    (
      "R1.5",
      "rejects a request carrying no authentication as unauthorized",
      fixture =>
        for
          _ <- article(fixture)
          response <- fixture.call(
            Method.POST,
            "/api/articles/dragons/comments",
            """{"comment":{"body":"anonymous"}}""".some
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
