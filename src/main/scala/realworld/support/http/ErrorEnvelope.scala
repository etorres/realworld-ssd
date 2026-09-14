package realworld.support.http

import hearth.kindlings.circederivation.KindlingsEncoder
import io.circe.Encoder

/** The rejection body every endpoint renders, per system invariant S2: a map from field name to the
  * things wrong with it.
  */
final case class ErrorEnvelope(errors: Map[String, List[String]])

object ErrorEnvelope:
  given Encoder[ErrorEnvelope] = KindlingsEncoder.derived

  def of(fields: Iterable[(String, String)]): ErrorEnvelope =
    ErrorEnvelope(fields.groupMap(_._1)(_._2).view.mapValues(_.toList.distinct).toMap)

  val unauthorized: ErrorEnvelope = of(List("body" -> "unauthorized"))
  val notFound: ErrorEnvelope = of(List("body" -> "not found"))
