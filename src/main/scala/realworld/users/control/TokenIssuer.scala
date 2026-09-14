package realworld.users.control

import scala.concurrent.duration.FiniteDuration

import cats.effect.kernel.{Clock, Sync}
import cats.syntax.all.*
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim, JwtOptions}

import realworld.users.entity.{AuthToken, UserId}

/** Mints and reads back the tokens that prove a caller's identity. */
trait TokenIssuer[F[_]]:
  def issue(id: UserId): F[AuthToken]

  /** The account a token was issued for, or nothing when the token is expired (R5.2), malformed, or not
    * signed by us (R5.3).
    */
  def subjectOf(token: AuthToken): F[Option[UserId]]

object TokenIssuer:

  def hs256[F[_]: Sync](secret: String, ttl: FiniteDuration): TokenIssuer[F] = new TokenIssuer[F]:

    def issue(id: UserId): F[AuthToken] =
      Clock[F].realTime.flatMap: now =>
        val issuedAt = now.toSeconds
        val claim = JwtClaim(
          subject = id.asString.some,
          issuedAt = issuedAt.some,
          expiration = (issuedAt + ttl.toSeconds).some
        )
        Sync[F].delay(AuthToken(JwtCirce.encode(claim, secret, JwtAlgorithm.HS256)))

    def subjectOf(token: AuthToken): F[Option[UserId]] =
      Clock[F].realTime.map: now =>
        // Expiry is checked against the effect's own clock rather than jwt-scala's system clock, so that
        // R5.2 is testable under TestControl instead of by waiting.
        JwtCirce
          .decode(token.value, secret, Seq(JwtAlgorithm.HS256), JwtOptions(expiration = false))
          .toOption
          .filter(_.expiration.forall(_ > now.toSeconds))
          .flatMap(_.subject)
          .flatMap(UserId.parse)
