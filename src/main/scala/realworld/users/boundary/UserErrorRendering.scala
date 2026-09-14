package realworld.users.boundary

import cats.syntax.all.*
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.{Response, Status}

import realworld.support.http.ErrorEnvelope
import realworld.users.entity.UserError

/** How a `UserError` reaches the wire, per system invariant S2.
  *
  * It lives in this component's boundary because the mapping is part of this component's contract, and it
  * is shared because every BC that resolves a caller through `authenticate-token` can be handed one.
  */
object UserErrorRendering:

  def response[F[_]](error: UserError): Response[F] =
    val status = error match
      case _: UserError.Invalid => Status.UnprocessableContent
      case _: UserError.Conflict => Status.Conflict
      // The DSL's `Unauthorized` demands a WWW-Authenticate challenge, which this API does not use.
      case _: UserError.Unauthorized => Status.Unauthorized
    Response[F](status).withEntity(ErrorEnvelope.of(error.fieldErrors.toList.map(f => f.field -> f.message)))
