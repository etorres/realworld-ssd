/** # Tags
  * > Own the set of tags in use across the system, so readers can discover what is being written about.
  *
  * ## Boundary
  * - `register-tags` — add the tags an article declares to the set in use
  * - `list-tags` — return every tag in use
  *
  * ## Requirements
  *
  * ### R1: Register tags in use
  * - R1.1 — When tags are registered, the BC shall add each of them to the set of tags in use.
  * - R1.2 — When a tag that is already in use is registered again, the BC shall leave the set unchanged.
  * - R1.3 — If a registered tag is blank, then the BC shall ignore it. _(why: `articles` should not have to
  *   sanitise before calling)_
  *
  * ### R2: List tags in use
  * - R2.1 — When the tags in use are requested, the BC shall return every registered tag exactly once.
  * - R2.2 — While no tag has been registered, the BC shall return an empty list.
  *
  * ## Entities
  * - Tag
  *
  * ## Out of scope
  * - Removing a tag when the last article carrying it is deleted — the registry is append-only, see
  *   decision D5 in the system doc
  * - Ranking tags by popularity or counting how many articles carry each one
  * - Renaming, merging, and aliasing tags
  * - Deciding which tags an article carries (owned by `articles`)
  */
package realworld.tags
