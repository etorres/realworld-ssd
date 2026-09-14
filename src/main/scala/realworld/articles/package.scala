/** # Articles
  * > Own authored posts: publishing them under a stable slug, browsing and filtering them, and recording
  * who favorited what.
  *
  * ## Boundary
  * - `publish-article` — create an article authored by the caller
  * - `list-articles` — browse articles, newest first, narrowed by tag, author, or favoriting user
  * - `read-feed` — browse articles authored by the users the caller follows
  * - `read-article` — return one article by its slug
  * - `update-article` — change the caller's own article
  * - `delete-article` — withdraw the caller's own article
  * - `favorite-article` — record the caller's favorite of an article
  * - `unfavorite-article` — withdraw the caller's favorite of an article
  *
  * ## Requirements
  *
  * ### R1: Publish an article
  * - R1.1 — When an authenticated caller submits a non-blank title, description, and body, the BC shall
  *   create the article with the caller as its author, derive a slug from the title, stamp its creation and
  *   update times, and return it.
  * - R1.2 — When a tag list is submitted, the BC shall attach each distinct tag to the article in the
  *   order given, and register those tags as being in use.
  * - R1.3 — When no tag list is submitted, the BC shall create the article with an empty tag list.
  * - R1.4 — When an article is created, the BC shall report it as not favorited by the caller and with a
  *   favorites count of zero.
  * - R1.5 — If the derived slug is already taken, then the BC shall derive a distinct slug rather than
  *   reject the request. _(why: two authors may legitimately choose the same title)_
  * - R1.6 — If the title, the description, or the body is blank, then the BC shall reject the request as a
  *   validation failure naming every blank field.
  * - R1.7 — If the request carries no authentication, then the BC shall reject it as unauthorized.
  *
  * ### R2: List articles
  * - R2.1 — When articles are listed without filters, the BC shall return them most recently created first,
  *   together with the total number of articles matching the request.
  * - R2.2 — When a tag filter is supplied, the BC shall return only articles carrying that tag.
  * - R2.3 — When an author filter is supplied, the BC shall return only articles written by that user.
  * - R2.4 — When a favoriting-user filter is supplied, the BC shall return only articles that user has
  *   favorited.
  * - R2.5 — When more than one filter is supplied, the BC shall return only articles satisfying all of them.
  * - R2.6 — When a limit and an offset are supplied, the BC shall return at most that many articles starting
  *   at that offset, while the reported total shall still count every matching article.
  * - R2.7 — Where no limit or offset is supplied, the BC shall return at most 20 articles starting at the
  *   first one.
  * - R2.8 — While the caller is authenticated, the BC shall report for each article whether the caller has
  *   favorited it and whether the caller follows its author.
  * - R2.9 — If a filter names a user who is not registered, then the BC shall return no articles and a total
  *   of zero.
  * - R2.10 — When articles are listed, the BC shall omit each article's body from the listing. _(why: a
  *   listing is for browsing; the body is what reading one article is for)_
  *
  * ### R3: Read the feed
  * - R3.1 — When an authenticated caller reads the feed, the BC shall return only articles authored by the
  *   users that caller follows, most recently created first, with the total number of such articles.
  * - R3.2 — When a limit and an offset are supplied, the BC shall return at most that many feed articles
  *   starting at that offset, while the reported total shall still count every article in the feed.
  * - R3.3 — Where no limit or offset is supplied, the BC shall return at most 20 feed articles starting at
  *   the first one.
  * - R3.4 — While the caller follows nobody, the BC shall return no articles and a total of zero.
  * - R3.5 — If the request carries no authentication, then the BC shall reject it as unauthorized.
  * - R3.6 — When the feed is read, the BC shall omit each article's body from the listing.
  *
  * ### R4: Read an article
  * - R4.1 — When an existing slug is requested, the BC shall return the article with its author, tags,
  *   favorites count, and creation and update times.
  * - R4.2 — While the caller is authenticated, the BC shall report whether the caller has favorited the
  *   article and whether the caller follows its author.
  * - R4.3 — If no article exists under the requested slug, then the BC shall reject the request as not
  *   found.
  *
  * ### R5: Update an article
  * - R5.1 — When the author submits any subset of title, description, and body, the BC shall apply exactly
  *   the submitted fields, leave every omitted field unchanged, and advance the update time.
  * - R5.2 — When the title changes, the BC shall derive a new slug from it and serve the article under that
  *   slug thereafter.
  * - R5.3 — If a submitted title, description, or body is blank, then the BC shall reject the request as a
  *   validation failure naming every blank field.
  * - R5.4 — If the caller is authenticated but is not the article's author, then the BC shall reject the
  *   request as forbidden.
  * - R5.5 — If no article exists under the requested slug, then the BC shall reject the request as not
  *   found.
  * - R5.6 — If the request carries no authentication, then the BC shall reject it as unauthorized.
  * - R5.7 — When a tag list is submitted, the BC shall replace the article's tags with exactly that list,
  *   in the order given, and register any tag newly in use.
  * - R5.8 — When no tag list is submitted, the BC shall leave the article's tags unchanged.
  * - R5.9 — When an empty tag list is submitted, the BC shall remove every tag from the article.
  * - R5.10 — If a tag list is submitted with no value, then the BC shall reject the request as a validation
  *   failure naming the tag list. _(why: an omitted list means leave them alone and an empty one means
  *   remove them all, so a valueless one means neither)_
  *
  * ### R6: Delete an article
  * - R6.1 — When the author deletes their article, the BC shall remove it together with every favorite of
  *   it, and shall thereafter report its slug as not found.
  * - R6.2 — If the caller is authenticated but is not the article's author, then the BC shall reject the
  *   request as forbidden.
  * - R6.3 — If no article exists under the requested slug, then the BC shall reject the request as not
  *   found.
  * - R6.4 — If the request carries no authentication, then the BC shall reject it as unauthorized.
  *
  * ### R7: Favorite an article
  * - R7.1 — When an authenticated caller favorites an article they have not yet favorited, the BC shall
  *   record the favorite, raise the favorites count by one, and return the article marked as favorited.
  * - R7.2 — When an authenticated caller favorites an article they have already favorited, the BC shall
  *   leave the favorites count unchanged and return the article marked as favorited.
  * - R7.3 — If no article exists under the requested slug, then the BC shall reject the request as not
  *   found.
  * - R7.4 — If the request carries no authentication, then the BC shall reject it as unauthorized.
  *
  * ### R8: Unfavorite an article
  * - R8.1 — When an authenticated caller unfavorites an article they have favorited, the BC shall remove the
  *   favorite, lower the favorites count by one, and return the article marked as not favorited.
  * - R8.2 — When an authenticated caller unfavorites an article they have not favorited, the BC shall leave
  *   the favorites count unchanged and return the article marked as not favorited.
  * - R8.3 — If no article exists under the requested slug, then the BC shall reject the request as not
  *   found.
  * - R8.4 — If the request carries no authentication, then the BC shall reject it as unauthorized.
  *
  * ## Entities
  * - Article
  * - Slug
  * - Favorite
  *
  * ## Out of scope
  * - Comments on an article (owned by `comments`)
  * - The set of tags in use across the system (owned by `tags`)
  * - Full-text search over titles and bodies
  * - Drafts, scheduled publication, and revision history
  */
package realworld.articles
