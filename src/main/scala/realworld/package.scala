/** # RealWorld
  * > Serve the RealWorld API contract: a social blogging backend where readers become authors, follow one
  * another, and curate what they read by tag.
  *
  * ## Vision
  * - Make the specification, not the code, the thing you edit first — and let the build tell you the moment
  *   the two disagree.
  *
  * ## Components
  * - `users` calls nothing. It is the root of the dependency graph.
  * - `profiles` may call `users` (`authenticate-token`, `describe-user`); never the reverse.
  * - `articles` may call `users` (`authenticate-token`, `describe-user`), `profiles` (`view-author`), and
  *   `tags` (`register-tags`); never the reverse.
  * - `comments` may call `users` (`authenticate-token`), `profiles` (`view-author`), and `articles`
  *   (`read-article`); never the reverse.
  * - `tags` calls nothing.
  *
  * ## System invariants
  * - S1 — If a request presents a token that is malformed, expired, or not issued by this system, or
  *   presents no token to an operation that requires one, then the system shall reject the request as
  *   unauthorized. _(why: every BC delegates caller identity to `users`, so the rejection rule must not be
  *   restated per BC; and an operation that merely accepts a token must still refuse a bad one rather than
  *   quietly treating its bearer as anonymous)_
  * - S2 — When the system rejects a request, it shall render the rejection as an error envelope carrying a
  *   field-keyed list of messages, and shall use the unauthorized status for a missing or failed
  *   authentication, the forbidden status for an authenticated caller lacking permission, the not-found
  *   status for an absent resource, the conflict status for a value already taken by somebody else, and
  *   the validation-failure status for an otherwise unusable payload.
  * - S3 — The system shall render every timestamp it reports as an ISO-8601 instant in UTC with
  *   millisecond precision. _(why: the platform's own rendering omits the fractional second when it
  *   happens to be zero, so the shape of a field would depend on what time it was written)_
  *
  * ## Ubiquitous language
  * - User — an account with credentials. Owned by `users`; only its owner ever reads it.
  * - Profile — the publicly visible projection of a User, plus whether the caller follows them. Owned by
  *   `profiles`.
  * - Caller — the identity resolved from the request's authentication token, or absent for an anonymous
  *   request.
  * - Article — an authored post identified by a slug derived from its title. Owned by `articles`.
  * - Slug — the URL-safe, unique identifier of an Article.
  * - Comment — a reply attached to one Article. Owned by `comments`.
  * - Tag — a free-text label an Article declares. The set in use is owned by `tags`.
  * - Feed — the Articles authored by the users the Caller follows, newest first.
  * - Favorite — a Caller's mark on an Article. Owned by `articles`.
  *
  * ## Decisions
  * - D1 — A capability spec lives in its component's `package.scala` as a Markdown scaladoc comment on the
  *   package clause. _(why: the closest Scala equivalent of `package-info.java` — co-located with the code
  *   and inside the compilation unit; rejected: a `spec.md` beside each package, a central `specs/` tree)_
  * - D2 — Domain errors travel in a Cats MTL typed channel: `control` requires `Raise[F, E]`, `boundary`
  *   closes the scope with `Handle.allow`/`rescue`. _(why: keeps the error type visible in the signature
  *   without a monad-transformer stack; rejected: `EitherT`, untyped `MonadThrow`)_
  * - D3 — Repositories are `Ref`-backed in-memory implementations behind per-component algebras.
  *   _(why: keeps the spec-test-code loop fast while the contract is still moving; rejected: Postgres via
  *   Skunk or Doobie from the start)_
  * - D4 — `users` owns both token issuance and token verification, exposing `authenticate-token` as a
  *   boundary operation. _(why: one owner for the credential contract, and the HTTP auth middleware becomes
  *   a thin caller rather than unspecified shared infrastructure; rejected: a shared auth package outside
  *   every capability spec)_
  * - D5 — `tags` owns an append-only registry that `articles` writes to through `register-tags`.
  *   _(why: the reverse direction would make `tags` scan `articles` and invert the dependency graph;
  *   rejected: `tags` deriving the set by reading articles)_
  * - D6 — SDD4J is the only spec workflow in this repository; SLDD is not used. _(why: two homes for
  *   requirements would manufacture the drift SDD4J exists to prevent; rejected: subordinating SLDD's gated
  *   steps to SDD4J, splitting the two by altitude)_
  * - D7 — A requirement id is traced by being the literal prefix of a munit test name. _(why: the id is
  *   runner-visible with no annotation machinery, and greppable in both directions; rejected: weaver,
  *   ScalaTest `AnyFeatureSpec`, a generated requirement enum)_
  * - D8 — JSON codecs are derived with Kindlings. _(why: Scala 3 native derivation without the
  *   `circe-generic` import surface; rejected: `circe-generic` semiauto, hand-written codecs)_
  * - D9 — The official RealWorld Hurl suite is the contract oracle; where it and the prose documentation
  *   disagree, the suite wins and the capability spec is corrected to match it. _(why: the RealWorld
  *   documentation itself says "the tests are the source of truth", and the two already disagree on the
  *   blank-field wording, on conflict being 409 rather than 422, and on the password length bounds;
  *   rejected: treating the prose endpoint documentation as authoritative)_
  * - D10 — An account's publicly shareable fields are read from `users` through `describe-user`; `profiles`
  *   composes them with its own follow state into a Profile. _(why: `profiles` is required to publish
  *   fields `users` owns, and no BC may read another's storage; rejected: `users` publishing account
  *   events for `profiles` to replicate, which buys eventual consistency for nothing while storage is
  *   in-memory; folding `profiles` back into `users` as one account capability)_
  * - D11 — Storage is PostgreSQL through Skunk, superseding D3's choice of in-memory repositories. The
  *   `Ref`-backed implementations are kept beside the new ones rather than deleted. _(why: D3 chose
  *   in-memory storage explicitly so it could be swapped once the contract stopped moving, and the swap
  *   is the test of whether these specifications describe behaviour or describe an implementation —
  *   keeping both implementations is what makes that a comparison rather than a rewrite; Skunk over
  *   Doobie because it stays in the effect system rather than wrapping JDBC's blocking model, and its
  *   codecs are written rather than derived; rejected: Doobie over JDBC, deleting the in-memory
  *   repositories, keeping D3 and treating durability as out of scope)_
  * - D12 — An article's identity is a TSID: a 64-bit identifier whose high bits are the millisecond it
  *   was minted in, so it sorts by creation order. _(why: R2.1 and R3.1 order articles by creation, and
  *   the tie between two created in the same instant was being settled by whatever the storage happened
  *   to return — the insertion order of a `Vector`, then PostgreSQL's heap, which moves when a row is
  *   rewritten; putting the order in the identity makes both implementations agree by construction
  *   rather than by coincidence. The identity is never published, since an article is addressed by its
  *   slug. Rejected: a `bigserial` column, which would fix only the SQL implementation and exists only
  *   to keep a test green; UUIDv7, whose sub-millisecond monotonicity is optional in RFC 9562 and so
  *   settles nothing; leaving the tie to the storage)_
  *
  * ## Stack
  * - Scala 3 + Typelevel (cats-effect · http4s · circe · cats-mtl) on sbt · base package `realworld`
  */
package realworld
