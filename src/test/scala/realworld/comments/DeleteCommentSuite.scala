package realworld.comments

import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Method, Status}

import realworld.RequirementSuite
import realworld.comments.entity.CommentError

import CommentsFixture.*

/** R3: Delete a comment. */
class DeleteCommentSuite extends RequirementSuite[CommentsFixture]:

  protected def setting: IO[CommentsFixture] = CommentsFixture()

  /** One article carrying one comment by jake. */
  private def commented(fixture: CommentsFixture) =
    for
      jake <- fixture.register("jake", "jake@jake.jake").map(_.user.id)
      _ <- fixture.publish(jake, "Dragons")
      comment <- accepted(fixture.comments.addComment(jake, "dragons", "It takes a Jacobian"))
    yield (jake, comment.comment.id.value)

  requirements(
    (
      "R3.1",
      "removes the comment and omits it from the article's comments thereafter",
      fixture =>
        for
          (jake, id) <- commented(fixture)
          survivor <- accepted(fixture.comments.addComment(jake, "dragons", "still here"))
          _ <- accepted(fixture.comments.deleteComment(jake, "dragons", id))
          remaining <- accepted(fixture.comments.listComments(None, "dragons"))
        yield
          assertEquals(remaining.map(_.comment.id.value), List(survivor.comment.id.value))
          assertEquals(remaining.map(_.comment.body), List("still here"))
    ),
    (
      "R3.2",
      "rejects an authenticated caller who is not the comment's author as forbidden",
      fixture =>
        for
          (_, id) <- commented(fixture)
          rival <- fixture.register("rival", "rival@jake.jake").map(_.user.id)
          error <- rejected(fixture.comments.deleteComment(rival, "dragons", id))
          survived <- accepted(fixture.comments.listComments(None, "dragons"))
        yield
          assertEquals(error, CommentError.NotTheAuthor)
          assertEquals(survived.size, 1, "the refused delete took effect anyway")
    ),
    (
      "R3.3",
      "rejects an identifier that is not attached to that article as not found",
      fixture =>
        for
          (jake, id) <- commented(fixture)
          _ <- fixture.publish(jake, "Other")
          unknown <- rejected(fixture.comments.deleteComment(jake, "dragons", 99999L))
          // A real comment, but on a different article: still nothing to delete here.
          elsewhere <- rejected(fixture.comments.deleteComment(jake, "other", id))
        yield
          assertEquals(unknown, CommentError.NoSuchComment)
          assertEquals(elsewhere, CommentError.NoSuchComment)
    ),
    (
      "R3.4",
      "rejects a request carrying no authentication as unauthorized",
      fixture =>
        for
          (_, id) <- commented(fixture)
          response <- fixture.call(Method.DELETE, s"/api/articles/dragons/comments/$id")
          body <- response.as[Json]
        yield
          assertEquals(response.status, Status.Unauthorized)
          assertEquals(
            body.hcursor.downField("errors").downField("token").as[List[String]],
            Right(List("is missing"))
          )
    ),
    (
      "R3.5",
      "rejects an unknown article slug as not found, naming the article rather than the comment",
      fixture =>
        for
          (jake, id) <- commented(fixture)
          error <- rejected(fixture.comments.deleteComment(jake, "no-such-article", id))
          session <- fixture.register("other", "other@jake.jake")
          response <- fixture.call(
            Method.DELETE,
            s"/api/articles/no-such-article/comments/$id",
            token = session.token.some
          )
          body <- response.as[Json]
        yield
          assertEquals(error, CommentError.NoSuchArticle)
          assertEquals(response.status, Status.NotFound)
          // The rejection must name the article, not the comment.
          assertEquals(
            body.hcursor.downField("errors").downField("article").as[List[String]],
            Right(List("not found")),
            s"the wrong thing was reported missing: $body"
          )
    )
  )
