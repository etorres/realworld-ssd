package realworld.profiles.entity

import realworld.users.entity.UserId

/** One account's follow of another. Held as a set, so following twice is the same as following once. */
final case class Follow(follower: UserId, followed: UserId)
