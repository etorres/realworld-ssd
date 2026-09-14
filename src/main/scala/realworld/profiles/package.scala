/** # Profiles
  * > Publish the public face of an account and own who follows whom.
  *
  * ## Boundary
  * - `view-profile` — return a user's public profile as the calling caller sees it
  * - `follow-user` — record that the caller follows a user
  * - `unfollow-user` — withdraw the caller's follow of a user
  * - `view-author` — return the profile of an account already known by identity, as the caller sees it
  *   _(why: a BC that stores a reference to an account needs its rendered profile without knowing its
  *   current name, and composing one outside this BC would put the projection in two places)_
  *
  * ## Requirements
  *
  * ### R1: View a profile
  * - R1.1 — When an existing username is requested by an anonymous caller, the BC shall return that user's
  *   username, bio, and image with the follow flag unset.
  * - R1.2 — When an existing username is requested by an authenticated caller who follows that user, the BC
  *   shall return the profile with the follow flag set.
  * - R1.3 — When an existing username is requested by an authenticated caller who does not follow that
  *   user, the BC shall return the profile with the follow flag unset.
  * - R1.4 — When a caller requests their own profile, the BC shall return it with the follow flag unset.
  *   _(why: a user never follows themselves)_
  * - R1.5 — If no user is registered under the requested username, then the BC shall reject the request as
  *   not found.
  *
  * ### R2: Follow a user
  * - R2.1 — When an authenticated caller follows a user they do not yet follow, the BC shall record the
  *   relationship and return the profile with the follow flag set.
  * - R2.2 — When an authenticated caller follows a user they already follow, the BC shall leave the
  *   relationship unchanged and return the profile with the follow flag set.
  * - R2.3 — If the caller names themselves, then the BC shall reject the request as a validation failure.
  * - R2.4 — If no user is registered under the requested username, then the BC shall reject the request as
  *   not found.
  * - R2.5 — If the request carries no authentication, then the BC shall reject it as unauthorized.
  *
  * ### R3: Unfollow a user
  * - R3.1 — When an authenticated caller unfollows a user they follow, the BC shall remove the relationship
  *   and return the profile with the follow flag unset.
  * - R3.2 — When an authenticated caller unfollows a user they do not follow, the BC shall leave the
  *   relationships unchanged and return the profile with the follow flag unset.
  * - R3.3 — If no user is registered under the requested username, then the BC shall reject the request as
  *   not found.
  * - R3.4 — If the request carries no authentication, then the BC shall reject it as unauthorized.
  *
  * ### R4: View a known account's profile
  * - R4.1 — When the identity of a registered account is presented, the BC shall return that account's
  *   profile as the caller sees it, with the follow flag set exactly as `view-profile` would set it.
  * - R4.2 — If no account holds the presented identity, then the BC shall report that none exists. _(why:
  *   the caller already holds the identity, so absence is a broken reference rather than a bad request)_
  *
  * ## Entities
  * - Profile
  * - Follow
  *
  * ## Out of scope
  * - Account credentials and the owner-only account view (owned by `users`)
  * - Listing a user's followers or the users they follow
  * - Blocking, muting, and follow requests requiring approval
  * - Notifying a user that they have been followed
  */
package realworld.profiles
