package realworld.profiles.control

import cats.Monad
import cats.mtl.Raise
import cats.mtl.syntax.all.*
import cats.syntax.all.*

import realworld.profiles.entity.{Follow, Profile, ProfileError}
import realworld.users.boundary.Users
import realworld.users.control.PublicAccount
import realworld.users.entity.UserId

/** The `profiles` use cases.
  *
  * The account's own fields come from `users` through `describe-user`; this BC contributes the follow
  * state and composes the two into a Profile (decision D10).
  */
final class ProfileService[F[_]: Monad](accounts: Users[F], follows: FollowRepository[F]):

  def view(caller: Option[UserId], username: String)(using Raise[F, ProfileError]): F[Profile] =
    for
      account <- existing(username)
      // A caller never follows themselves, so their own profile reads as unfollowed (R1.4) without a
      // special case: `follow` refuses to record one in the first place.
      following <- caller.fold(false.pure[F])(follows.isFollowing(_, account.id))
    yield profileOf(account, following)

  def follow(caller: UserId, username: String)(using Raise[F, ProfileError]): F[Profile] =
    for
      account <- existing(username)
      _ <- ProfileError.CannotFollowSelf.raise[F, Unit].whenA(account.id == caller)
      _ <- follows.add(Follow(caller, account.id))
    yield profileOf(account, following = true)

  def unfollow(caller: UserId, username: String)(using Raise[F, ProfileError]): F[Profile] =
    for
      account <- existing(username)
      _ <- follows.remove(Follow(caller, account.id))
    yield profileOf(account, following = false)

  /** Absence is a broken reference rather than a bad request (R4.2), so this reports rather than raises. */
  def author(account: UserId, caller: Option[UserId]): F[Option[Profile]] =
    accounts
      .describeUser(account)
      .flatMap:
        _.traverse: known =>
          caller.fold(false.pure[F])(follows.isFollowing(_, known.id)).map(profileOf(known, _))

  private def existing(username: String)(using Raise[F, ProfileError]): F[PublicAccount] =
    accounts
      .describeUser(username)
      .flatMap(_.fold(ProfileError.NoSuchProfile.raise[F, PublicAccount])(_.pure[F]))

  private def profileOf(account: PublicAccount, following: Boolean): Profile =
    Profile(account.username, account.bio, account.image, following)
