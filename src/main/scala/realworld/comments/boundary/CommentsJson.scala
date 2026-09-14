package realworld.comments.boundary

import java.time.Instant

import hearth.kindlings.circederivation.{KindlingsCodecAsObject, KindlingsDecoder}
import io.circe.{Codec, Decoder}

import realworld.articles.boundary.AuthorPayload
import realworld.comments.control.CommentView
import realworld.support.http.Timestamps.given

/** The wire shape of the `comments` boundary, exactly as the RealWorld API defines it. */
final case class CommentPayload(
    id: Long,
    createdAt: Instant,
    updatedAt: Instant,
    body: String,
    author: AuthorPayload
)

object CommentPayload:
  given Codec.AsObject[CommentPayload] = KindlingsCodecAsObject.derived

  def from(view: CommentView): CommentPayload =
    CommentPayload(
      id = view.comment.id.value,
      createdAt = view.comment.createdAt,
      updatedAt = view.comment.updatedAt,
      body = view.comment.body,
      author = AuthorPayload.from(view.author)
    )

final case class CommentBody(comment: CommentPayload)

object CommentBody:
  given Codec.AsObject[CommentBody] = KindlingsCodecAsObject.derived

  def from(view: CommentView): CommentBody = CommentBody(CommentPayload.from(view))

final case class CommentsBody(comments: List[CommentPayload])

object CommentsBody:
  given Codec.AsObject[CommentsBody] = KindlingsCodecAsObject.derived

  def from(views: List[CommentView]): CommentsBody = CommentsBody(views.map(CommentPayload.from))

/** The body stays optional so that a missing one becomes "can't be blank" from the domain (R1.3). */
final case class AddCommentPayload(body: Option[String])

final case class AddCommentRequest(comment: AddCommentPayload)

object AddCommentRequest:
  given Decoder[AddCommentRequest] = KindlingsDecoder.derived
