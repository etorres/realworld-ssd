package realworld.users.boundary

import cats.Applicative
import cats.mtl.Raise
import cats.mtl.syntax.all.*
import cats.syntax.all.*
import org.http4s.Request
import org.typelevel.ci.CIString

import realworld.users.entity.{AuthToken, UserError, UserId}

/** Resolves the caller behind a request.
  *
  * Every component that needs an identity comes through here rather than reading the header itself,
  * because `users` owns token verification (decision D4) and system invariant S1 fixes the rejection rule
  * once for the whole system.
  */
final class CallerAuth[F[_]: Applicative](users: Users[F]):

  def require(request: Request[F])(using Raise[F, UserError]): F[UserId] =
    CallerAuth.tokenOf(request).fold(UserError.missingToken.raise[F, UserId])(users.authenticateToken)

  /** For operations an anonymous caller may also perform. A token that is present must still be good: no
    * authentication at all is anonymity, a bad token is a rejection (S1).
    */
  def optional(request: Request[F])(using Raise[F, UserError]): F[Option[UserId]] =
    CallerAuth.tokenOf(request).traverse(users.authenticateToken)

object CallerAuth:
  private val Header = CIString("Authorization")

  /** RealWorld sends `Authorization: Token <jwt>`; `Bearer` is accepted too, since clients send both. */
  private val Schemes = List("token", "bearer")

  def tokenOf[F[_]](request: Request[F]): Option[AuthToken] =
    request.headers
      .get(Header)
      .map(_.head.value.trim)
      .flatMap: raw =>
        Schemes.collectFirst:
          case scheme if raw.toLowerCase.startsWith(s"$scheme ") => raw.drop(scheme.length + 1).trim
      .filter(_.nonEmpty)
      .map(AuthToken.apply)
