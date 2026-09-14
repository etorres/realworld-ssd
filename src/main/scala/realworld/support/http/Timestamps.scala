package realworld.support.http

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}

import io.circe.Encoder

/** How an instant reaches the wire: ISO-8601 UTC with milliseconds, always (system invariant S3).
  *
  * `Instant.toString` omits the fractional second entirely when the nanoseconds are zero, rendering the
  * same field as `…T00:00:00Z` about one write in a thousand and `…T00:00:00.410Z` the rest of the time.
  * Fixing the pattern makes the shape constant.
  */
object Timestamps:
  private val Iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

  def render(instant: Instant): String = Iso.format(instant)

  given Encoder[Instant] = Encoder.encodeString.contramap(render)
