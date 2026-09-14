/** # Users
  * > Own account identity: registration, credential authentication, token issuance, and the account view
  * that only its owner may read.
  *
  * ## Boundary
  * - `register-user` — create an account from a username, an email, and a password, returning it with an
  *   authentication token
  * - `authenticate-user` — exchange an email and a password for the account and a fresh token
  * - `get-current-user` — return the authenticated caller's own account
  * - `update-user` — change any subset of the caller's email, username, password, bio, and image
  * - `authenticate-token` — resolve an authentication token to the identity of the account it was issued
  *   for _(why: every other BC needs the caller's identity, and issuing and verifying a token must have a
  *   single owner)_
  * - `describe-user` — report an account's publicly shareable fields by username _(why: `profiles` must
  *   publish the account's public face and cannot reach into this BC's storage for it)_
  *
  * ## Requirements
  *
  * ### R1: Register a user
  * - R1.1 — When a registration carries a username, an email, and a password that are all well-formed and
  *   unused, the BC shall create the account and return it with a freshly issued authentication token.
  * - R1.2 — If the email is already registered, then the BC shall reject the registration as a conflict
  *   naming the email field.
  * - R1.3 — If the username is already taken, then the BC shall reject the registration as a conflict
  *   naming the username field.
  * - R1.4 — If the username, the email, or the password is blank, then the BC shall reject the registration
  *   as a validation failure naming every blank field.
  * - R1.5 — If the email is not a well-formed address, then the BC shall reject the registration as a
  *   validation failure naming the email field.
  * - R1.6 — If the password is shorter than 8 characters, then the BC shall reject the registration as a
  *   validation failure naming the password field. _(why: NIST SP 800-63B §5.1.1.2)_
  * - R1.7 — The BC shall accept a password of at least 64 characters. _(why: NIST SP 800-63B §5.1.1.2
  *   forbids a low upper bound)_
  * - R1.8 — When an account is created, the BC shall retain its password only as an irreversible hash and
  *   shall never disclose the password or the hash. _(why: the credential is the one value this BC can
  *   never be allowed to leak)_
  * - R1.9 — When an account is created, the BC shall leave its bio and its image absent.
  *
  * ### R2: Authenticate a user
  * - R2.1 — When an email and a password matching a registered account are submitted, the BC shall return
  *   that account with a freshly issued authentication token.
  * - R2.2 — If no account is registered for the submitted email, then the BC shall reject the request as
  *   unauthorized, reporting the credentials as invalid.
  * - R2.3 — If the submitted password does not match the account's stored credential, then the BC shall
  *   reject the request as unauthorized, reporting the credentials as invalid. _(why: the rejection must
  *   not reveal whether the address is registered)_
  * - R2.4 — If the email or the password is blank, then the BC shall reject the request as a validation
  *   failure naming every blank field.
  *
  * ### R3: Read the current account
  * - R3.1 — While the caller is authenticated, the BC shall return the caller's own account with a valid
  *   authentication token.
  * - R3.2 — If the request carries no authentication, then the BC shall reject it as unauthorized,
  *   reporting the token as missing.
  *
  * ### R4: Update the current account
  * - R4.1 — When an authenticated caller submits any subset of email, username, password, bio, and image,
  *   the BC shall apply exactly the submitted fields and leave every omitted field unchanged.
  * - R4.2 — When the caller submits an empty bio or image, the BC shall clear that field. _(why: the
  *   contract distinguishes a field left out from a field deliberately cleared)_
  * - R4.3 — When a new password is submitted, the BC shall replace the stored credential so that only the
  *   new password authenticates the account afterwards.
  * - R4.4 — When the caller resubmits their own current email or username unchanged, the BC shall accept
  *   the update. _(why: a uniqueness check that does not exclude the caller makes every profile edit fail)_
  * - R4.5 — If the submitted email is already registered to a different account, then the BC shall reject
  *   the update as a conflict naming the email field.
  * - R4.6 — If the submitted username is already taken by a different account, then the BC shall reject the
  *   update as a conflict naming the username field.
  * - R4.7 — If a submitted email, username, or password is empty, then the BC shall reject the update as a
  *   validation failure naming every empty field. _(why: unlike bio and image, these three have no
  *   cleared state)_
  * - R4.8 — If the submitted password is shorter than 8 characters, then the BC shall reject the update as
  *   a validation failure naming the password field.
  * - R4.9 — If the request carries no authentication, then the BC shall reject it as unauthorized,
  *   reporting the token as missing.
  *
  * ### R5: Authenticate a token
  * - R5.1 — When a token this BC issued is presented within its validity window, the BC shall resolve it to
  *   the identity of the account it was issued for.
  * - R5.2 — If the presented token has expired, then the BC shall reject it as unauthorized.
  * - R5.3 — If the presented token is malformed or its signature does not verify, then the BC shall reject
  *   it as unauthorized.
  * - R5.4 — If the presented token resolves to an account that no longer exists, then the BC shall reject
  *   it as unauthorized.
  *
  * ### R6: Describe an account
  * - R6.1 — When a username identifying a registered account is presented, the BC shall report that
  *   account's identity, username, bio, and image.
  * - R6.2 — If no account is registered under the presented username, then the BC shall report that no
  *   such account exists. _(why: absence is an answer here, not a rejection; whether it becomes one is the
  *   asking BC's decision)_
  * - R6.3 — The BC shall never report an account's email or credential through this operation.
  * - R6.4 — When an identity of a registered account is presented, the BC shall report the same fields as
  *   it reports for that account's username. _(why: a username can change, so a BC holding a reference to
  *   an account holds its identity, not its name)_
  *
  * ## Entities
  * - User
  * - Credential
  * - AuthToken
  *
  * ## Out of scope
  * - The Profile projection, the follow flag, and follow relationships (owned by `profiles`)
  * - Password reset, email verification, and account deletion
  * - Token revocation and refresh
  * - Rate limiting and lockout after repeated failed authentications
  * - Checking a password against a breach corpus, and any composition rule beyond the length bounds in
  *   R1.6 and R1.7
  */
package realworld.users
