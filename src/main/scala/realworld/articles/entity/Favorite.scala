package realworld.articles.entity

import realworld.users.entity.UserId

/** One caller's mark on one article. Held as a set, so favoriting twice is the same as favoriting once. */
final case class Favorite(user: UserId, article: ArticleId)
