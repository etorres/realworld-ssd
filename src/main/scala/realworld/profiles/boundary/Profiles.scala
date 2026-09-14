package realworld.profiles.boundary

import cats.effect.kernel.Sync
import cats.mtl.Raise
import cats.syntax.all.*

import realworld.profiles.control.{FollowRepository, ProfileService}
import realworld.profiles.entity.{Profile, ProfileError}
import realworld.users.boundary.Users
import realworld.users.entity.UserId

/** The contract of the `profiles` business component. Each method realises one `## Boundary` operation. */
trait Profiles[F[_]]:
  /** The caller is optional: an anonymous reader sees the profile with the follow flag unset (R1.1). */
  def viewProfile(caller: Option[UserId], username: String)(using Raise[F, ProfileError]): F[Profile]

  def followUser(caller: UserId, username: String)(using Raise[F, ProfileError]): F[Profile]

  def unfollowUser(caller: UserId, username: String)(using Raise[F, ProfileError]): F[Profile]

  /** For a BC holding a reference to an account. Reports nothing when the reference is broken (R4.2). */
  def viewAuthor(account: UserId, caller: Option[UserId]): F[Option[Profile]]

object Profiles:

  def apply[F[_]](service: ProfileService[F]): Profiles[F] = new Profiles[F]:

    def viewProfile(caller: Option[UserId], username: String)(using Raise[F, ProfileError]): F[Profile] =
      service.view(caller, username)

    def followUser(caller: UserId, username: String)(using Raise[F, ProfileError]): F[Profile] =
      service.follow(caller, username)

    def unfollowUser(caller: UserId, username: String)(using Raise[F, ProfileError]): F[Profile] =
      service.unfollow(caller, username)

    def viewAuthor(account: UserId, caller: Option[UserId]): F[Option[Profile]] =
      service.author(account, caller)

  /** The whole component, assembled over in-memory storage (decision D3). */
  def inMemory[F[_]: Sync](accounts: Users[F]): F[Profiles[F]] =
    FollowRepository.inMemory[F].map(follows => apply(ProfileService(accounts, follows)))
