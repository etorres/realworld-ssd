package realworld.users.control

import cats.effect.kernel.Sync
import cats.syntax.all.*
import org.mindrot.jbcrypt.BCrypt

import realworld.users.entity.{Credential, HashedPassword, Password}

/** Turns a password into something R1.6 permits us to keep, and checks one against it. */
trait PasswordHasher[F[_]]:
  def hash(password: Password): F[Credential]
  def verify(password: Password, credential: Credential): F[Boolean]

object PasswordHasher:

  /** bcrypt at the given cost. Both operations are deliberately CPU-expensive, so they run on the blocking
    * pool rather than starving the compute pool.
    */
  def bcrypt[F[_]: Sync](logRounds: Int): PasswordHasher[F] = new PasswordHasher[F]:
    def hash(password: Password): F[Credential] =
      Sync[F]
        .blocking(BCrypt.hashpw(password.value, BCrypt.gensalt(logRounds)))
        .map: hashed =>
          Credential(HashedPassword.fromHash(hashed))

    def verify(password: Password, credential: Credential): F[Boolean] =
      Sync[F].blocking(BCrypt.checkpw(password.value, credential.hash.value))
