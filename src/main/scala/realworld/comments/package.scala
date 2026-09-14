/** # Comments
  * > Own the replies attached to an article and who may withdraw them.
  *
  * ## Boundary
  * - `add-comment` — attach the caller's reply to an article
  * - `list-comments` — return every reply on an article
  * - `delete-comment` — withdraw the caller's own reply
  *
  * ## Requirements
  *
  * ### R1: Add a comment
  * - R1.1 — When an authenticated caller submits a non-blank body for an existing article, the BC shall
  *   create the comment with the caller as its author, stamp its creation and update times, and return it.
  * - R1.2 — When a comment is created, the BC shall give it a whole-number identifier, unique across every
  *   comment. _(why: the identifier is addressed in a request path and read back as a number)_
  * - R1.3 — If the body is blank, then the BC shall reject the request as a validation failure naming the
  *   body field.
  * - R1.4 — If no article exists under the requested slug, then the BC shall reject the request as not
  *   found.
  * - R1.5 — If the request carries no authentication, then the BC shall reject it as unauthorized.
  *
  * ### R2: List comments
  * - R2.1 — When comments are requested for an existing article, the BC shall return every comment attached
  *   to it, most recently created first.
  * - R2.2 — While the caller is authenticated, the BC shall report for each comment whether the caller
  *   follows its author.
  * - R2.3 — While an article carries no comments, the BC shall return an empty list.
  * - R2.4 — If no article exists under the requested slug, then the BC shall reject the request as not
  *   found.
  *
  * ### R3: Delete a comment
  * - R3.1 — When the comment's author deletes it, the BC shall remove it and shall thereafter omit it from
  *   that article's comments.
  * - R3.2 — If the caller is authenticated but is not the comment's author, then the BC shall reject the
  *   request as forbidden.
  * - R3.3 — If no comment with the requested identifier is attached to that article, then the BC shall
  *   reject the request as not found.
  * - R3.4 — If the request carries no authentication, then the BC shall reject it as unauthorized.
  * - R3.5 — If no article exists under the requested slug, then the BC shall reject the request as not
  *   found, naming the article rather than the comment. _(why: the two absences are different, and a
  *   caller with a stale slug should not be told the comment is missing)_
  *
  * ## Entities
  * - Comment
  *
  * ## Out of scope
  * - Removing an article's comments when the article itself is deleted — `articles` does not call
  *   `comments`, so such comments become unreachable rather than removed
  * - Nested replies and threading
  * - Editing a comment after it is posted
  * - Moderation, flagging, and hiding
  */
package realworld.comments
