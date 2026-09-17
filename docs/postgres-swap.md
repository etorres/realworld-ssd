# Experiment 2 — swapping the storage

> Decision D3 put every repository behind a `Ref`-backed in-memory implementation specifically so it
> could be swapped later. A correct swap should touch zero requirement statements and leave all 118
> traces intact. If it forces a spec change, that is the interesting result, because it means the
> specifications were describing the implementation all along.

This file is the pre-registration. It is written **before** the swap so the result cannot be decided
after the fact by whoever writes it up.

## How the result is judged

Three outcomes, fixed in advance:

| Outcome | What it looks like | What it means |
| --- | --- | --- |
| **Specs neutral** | `scripts/spec-baseline.sh` clean · `SpecTraceSuite` reports 118/118 · no test body changed except fixture construction | The specifications described behaviour, not storage |
| **Specs leaked** | A line disappears from `scripts/spec-baseline.sh`'s output — a statement reworded or retired | The specifications were describing the implementation |
| **Tests leaked** | Specs clean, traces intact, but a requirement's *assertions* had to change | The statements were neutral; the tests over-specified past them |

The third outcome is the one the write-up did not anticipate, and hazard **H1** below already makes it
the most likely.

### The pre-registered baseline

```bash
scripts/spec-baseline.sh          # verify: 118 statements unchanged
scripts/spec-baseline.sh record   # re-register (only after a deliberate spec change)
```

`SpecTraceSuite` guards the *set* of requirement ids: add one and the build stays red until a test
carries it. What it cannot see is a statement quietly reworded under the same id — the same trace, a
different promise. `scripts/spec-baseline.txt` hashes each statement's text, so rewording is as visible
as removal. Whitespace is normalised, so re-wrapping a line is not a change; a single altered word is.

It is deliberately *not* wired into `sbt test`. A spec change is legitimate during `/sdd4j new` work,
and a build gate that punishes it would be wrong. During this experiment it is the measurement
instrument, which is a different job.

Verified by mutation before being trusted: rewording an opening line, rewording only a continuation
line, and retiring a statement each produce a diff; re-wrapping the same words does not.

## The swap surface

The boundary traits are already storage-agnostic — `Users.apply(service)` takes an assembled service and
knows nothing about where it keeps things. Only the `inMemory` assemblers choose. That is the whole
reason this is tractable.

**Repository algebras** (the six `trait`s stay; their `inMemory` companions gain a sibling):

| Algebra | Storage it needs |
| --- | --- |
| `users/control/UserRepository` | one table; lookups by id, email, username |
| `profiles/control/FollowRepository` | one join table, `(follower, followed)` unique |
| `articles/control/ArticleRepository` | one table; `tags` is order-significant (R1.2, R5.7) |
| `articles/control/FavoriteRepository` | one join table, `(user, article)` unique |
| `comments/control/CommentRepository` | one table plus a sequence for `nextId` |
| `tags/control/TagRegistry` | one append-only table, insertion order preserved (R2.1) |

**Assemblers that name the decision:** `Users.inMemory`, `Profiles.inMemory`, `Tags.inMemory`,
`Articles.inMemory`, `Comments.inMemory`, and `Main.scala` which calls all five.

**Test fixtures:** `UsersFixture`, `ProfilesFixture`, `ArticlesFixture`, `CommentsFixture`,
`TagsFixture`. Only `UsersFixture` wires repositories by hand; the rest call the `inMemory` assemblers.

**Non-spec packages that absorb the rest:** `realworld.support.config` (connection settings) and
`realworld.Main` (wiring) are both declared outside the spec surface in `AGENTS.md`, so changes there
are free by construction.

## Hazards

Ranked by how likely each is to force a spec or test change. `PROVEN` means measured in this repository;
`DESIGN` means it follows from the code as written.

### H1 — the ordering tie-break has no key in Postgres · PROVEN · highest risk

`ArticleService.paged` sorts by `createdAt.toEpochMilli` — truncating to the millisecond — and relies on
`sortBy` being stable over `ArticleRepository.all`, whose scaladoc promises insertion order.

Measured: ten back-to-back publishes land at

```
…46.644556Z  …46.654643Z  …46.654932Z  …46.655037Z  …46.655153Z
…46.655259Z  …46.655375Z  …46.655472Z  …46.655580Z  …46.655676Z
```

**Seven of the ten share a millisecond with a neighbour.** The suite passes today only because
`ListArticlesSuite` publishes just three articles while the JIT is still cold, spacing them ~1 ms apart.
`SELECT … ORDER BY created_at DESC` returns rows in an unspecified order within equal keys, and articles
have no monotonic column to break the tie — `ArticleId` is a random UUID.

Affects articles R2.1, R2.6, R3.1, R3.2 and comments R2.1. Comments are the easy half: `CommentId` is a
monotonic `Long`, so `ORDER BY created_at DESC, id DESC` reproduces insertion order exactly.

Three ways out, none of which touches a statement — the requirement says "most recently created first"
and is silent about ties:

- **a** — order by `(created_at DESC, id DESC)` and accept a tie-break the tests do not predict, which
  means rewriting the assertions in `ListArticlesSuite` and `ReadFeedSuite`. This is outcome three.
- **b** — add a monotonic insertion column (`bigserial`) and order by it, restoring today's behaviour
  exactly. Choosing this *because* it keeps the tests green would be fitting the storage to the tests,
  and should be recorded as such.
- **c** — make `ArticleId` itself time-sortable. See below.

#### Candidate: a time-sorted `ArticleId` (TSID)

Not a decision. Recorded here so the option is on the table before the swap starts rather than invented
to explain the result afterwards.

`io.hypersistence:hypersistence-tsid` `2.1.4` — 64-bit, `[42-bit time][node][counter]`, zero runtime
dependencies, Java 8. Measured on a JVM probe against that version:

| Claim | Result |
| --- | --- |
| ten TSIDs from one factory, strictly increasing | **true** — and all ten landed in *one* millisecond |
| magnitude | `888309615823949876` (~8.9 × 10¹⁷) |
| exceeds JavaScript's `Number.MAX_SAFE_INTEGER` | **true**, by ~100× |
| cross-node, same millisecond: later id sorts higher | **false** |

The first row is the case that breaks ordering today, and it survives it. `TSID.Factory.getTime()`
increments a counter instead of re-reading the clock while `clock.millis() <= lastTime`, so one factory
is monotonic by construction rather than by luck.

Why this is better than **b**: **b** fixes ordering in Postgres and leaves `ArticleRepository.inMemory`
ordering by a different mechanism — two implementations agreeing by coincidence. A time-sorted id puts
the property in the *entity*, so both implementations order by the same field and `all` no longer has to
promise insertion order in its scaladoc. Storage-independent, which is what this experiment is about.

Spec-clean: no articles statement mentions an identifier — articles are addressed by slug — and
`ArticleId` is never serialized, since `ArticlesJson` emits no id field at all.

Three conditions if it is adopted:

1. **`created_at` stays the primary sort key; the id is the tie-break.** Two clocks are in play — the
   injected `Clock[F]` for `createdAt`, the library's own `java.time.Clock` for the id. Under
   `TestControl` (H2) virtual time puts `createdAt` in 1970 while the id's timestamp says 2026.
   `ORDER BY created_at DESC, id DESC` is unaffected; `ORDER BY id DESC` alone would be wrong. The
   builder does take `withClock`, but that interface is synchronous and cats-effect's virtual time
   cannot be exposed through it — do not try to unify them.
2. **Sub-millisecond order across servers is by node id, not creation time.** The node bits sit above the
   counter bits, so in the probe an id generated first on node 2 sorted below one generated second on
   node 1. R2.1 is satisfied either way, being silent on ties, but "sortable by time" holds only to
   millisecond resolution and should not be overclaimed.
3. **Do not extend it to `CommentId`.** Tempting, because `CommentRepository.nextId` is a centralised
   counter that genuinely does not survive multiple servers. But `CommentsJson` serializes `id` as a JSON
   number, and comments R1.2 exists precisely because "the identifier is addressed in a request path and
   read back as a number". At 8.9 × 10¹⁷ a JavaScript client silently loses precision — and the Hurl
   suite would *not* catch it, since Hurl handles `i64` fine. A 2⁵³ budget leaves 11 bits below the
   timestamp, which the library's fixed 22-bit random field cannot express.

**Sequencing matters more than the choice.** Adopting **c** before the swap means H1 never fires and the
finding disappears. Swap first with the naive `ORDER BY created_at DESC`, watch `ListArticlesSuite` R2.1
fail, and only then fix it. Otherwise the result is unreportable: a hazard silently removed before the
experiment that was supposed to expose it.

### H2 — `TestControl` cannot run over real I/O · PROVEN by inspection

`TestControl.executeEmbed` simulates time and requires every effect inside it to be simulatable. A
socket round-trip to Postgres never completes under virtual time. Three sites:

| Site | What it covers |
| --- | --- |
| `SystemInvariantSuite:42` | S1 — an expired token is rejected |
| `users/AuthenticateTokenSuite:38` | the same expiry, at the capability level |
| `articles/UpdateArticleSuite:47` | R5.1 — the update time advances past the creation time |

The first two need a clock, not storage, but run against a fixture whose repository would now be
DB-backed. The third genuinely interleaves writes with virtual time. Expect to replace virtual time with
an injected clock, or to keep these three suites on in-memory repositories and say so.

### H3 — there is no transaction boundary to put a transaction in · DESIGN

`ArticleService.delete` calls `favorites.removeAllOf(id)` and then `articles.delete(id)` — two
independent `F[Unit]`s. Articles R6.1 says the article is removed *together with* every favorite of it.
In-memory these are two separate `Ref` updates, so today's implementation is no more atomic; the
difference is that a database can fail between them and leave the orphans behind.

Making it atomic means threading a Skunk `Session[F]` (or a transaction handle) through the control
layer, which changes every repository signature. It is the largest structural change in the swap, and
it is entirely below the spec.

### H4 — uniqueness is checked in the service, on purpose · DESIGN

`UserRepository`'s scaladoc is explicit: uniqueness is *not* enforced in storage, because the service
checks it in order to name the offending field (users R1.2, R1.3, R4.2, R4.3). `ArticleService.available`
does the same for slugs (R1.5), looping until `findBySlug` misses.

Both are read-then-write. Under Postgres the honest version adds a unique index and catches `23505` —
and then has to map the constraint name back to a field name to keep R1.2 and R1.3 distinguishable in
the error envelope. Spec-neutral, fiddly, and easy to get subtly wrong in a way no current test catches
because nothing runs concurrently today.

### H5 — the obvious SQL optimisation is an architecture violation · DESIGN

`ArticleService.list` reads `articles.all` and `favorites.all` in full and filters in memory. Against a
database that is absurd, and the instinct is one query joining users, articles and favorites.

That join is forbidden. BCE lets a component reach another only through its boundary trait, and R2.9
("a filter naming an unregistered user matches nothing") is implemented by asking `users.describeUser`
first. `articles`' SQL may touch only `articles`' own tables. So the filtering can move into SQL, but
the username→id resolution stays a boundary call, and the feed's follow set stays a `profiles` call.

Worth watching: this is the point where the architecture will feel expensive, and the temptation to
widen a spec to justify a shortcut is highest.

### H6 — codecs for the opaque types · mechanical

`UserId`/`ArticleId` (UUID), `CommentId` (Long), and `Slug`/`Username`/`Email`/`HashedPassword`/
`AuthToken`/`Tag` (String) each need a Skunk `Codec`. `Article.tags: List[String]` is order-significant
(R1.2 and R5.7 both say "in the order given"), so a `text[]` column, or a join table with an explicit
position — never a `Set`.

### H7 — three tests write to storage directly · mechanical

`UsersFixture.stored` saves a `User` with a placeholder hash, and `DescribeUserSuite:78` and
`RegisterUserSuite:101` read the row back to prove R1.8 — that the password is never retained in the
clear. These are the tests most worth keeping honest against a real database, since that is where "what
is actually stored" stops being a figure of speech.

### H8 — the swap may falsify D3's stated reason, not the specs · DESIGN

D3's rationale is "keeps the spec-test-code loop fast while the contract is still moving." Today
`sbt test` runs 120 tests in about four seconds, and every fixture is an independent `Ref`, so everything runs
in parallel with no shared state.

One Postgres instance shared by 120 tests needs isolation — a schema per suite, a database per suite, or
a rolled-back transaction per test — and the loop gets slower whichever way it goes. The interesting
possibility is that the specifications survive untouched and *the decision record* is what turns out to
be wrong.

### H9 — S3 and column precision · minor

S3 fixes rendered timestamps at millisecond precision, and `Timestamps.render` implements it at the
boundary. `timestamptz` is microsecond-precision and round-trips an `Instant` faithfully; a column
declared `timestamp(3)` would silently change stored values. Declare full precision and leave S3's
rendering where it is.

### H10 — nothing verifies `support.config` · FOUND DURING THE SWAP

Adding the database settings to `AppConfig` introduced a `val` that referenced another `val` declared
below it. Scala initialises object fields in source order, so `database` was still `null` when `load`
captured it, and `sbt run` died with a `NullPointerException` from inside Ciris.

`sbt test` stayed green throughout. `realworld.support.config` is a declared non-spec package, so no
requirement traces it and no test loads it — the drift gate is silent there by construction, and the
first thing to notice was a server that would not boot.

Not a spec problem, and not an argument for specifying configuration. It is a reminder that the 118
traces cover the capabilities and nothing else, which is easy to forget when the report says 118/118.

## Dependencies (resolvable, checked)

| | Version | Notes |
| --- | --- | --- |
| `org.tpolecat::skunk-core` | `0.6.4` stable, or `1.0.0-M10` | both on cats-effect 3.5.x; 3.7.1 evicts upward |
| `com.dimafeng::testcontainers-scala-postgresql` | `0.43.0` | for a real database in `sbt test` |
| `io.hypersistence:hypersistence-tsid` | `2.1.4` | **candidate only**, see H1 · no runtime dependencies |

Skunk over Doobie for the reason D2 already gives: it stays in the Typelevel effect system rather than
wrapping JDBC's blocking model, and its codecs are explicit rather than derived.

## Order of work

1. **Freeze the baseline.** `scripts/spec-baseline.sh` and `git status` both clean. Already done.
2. **Record the decision first.** Add `D11` to the system doc — Postgres via Skunk, superseding D3's
   storage choice, with the rejected alternatives. A decision record is not a requirement statement, so
   it does not count against the experiment, but writing it *after* the code would be exactly the
   after-the-fact storytelling SDD4J exists to prevent.
3. **Schema and codecs** (H6), with the tables mirroring the entities one to one.
4. **One capability end to end** — `tags`, because it is five requirements and calls nothing. If the
   loop works there, it works.
5. **`users`**, which forces H4 and H7.
6. **`profiles`**, then **`articles`** (H1, H3, H5), then **`comments`**. Order articles the naive way
   first — `ORDER BY created_at DESC`, no tie-break — and record whether R2.1 fails before fixing it.
   Fixing H1 pre-emptively destroys the only measurement the hazard offers.
7. **Re-verify**: `scripts/spec-baseline.sh`, then `sbt test` for 118/118, then
   `scripts/api-conformance.sh` against a server on a real database.
8. **Write down which of the three outcomes happened**, including any test whose assertions changed and
   why — H1 makes that the likely story.

## Results

### `tags` — specs neutral

Both suites pass against a real PostgreSQL, and **the only thing that changed in the test tree was how
`TagsFixture` builds the component**. Not one row of `RegisterTagsSuite` or `ListTagsSuite` was touched.

```
tags 5/5 traced · 120 tests green · 118 statements unchanged since the baseline
```

Verified end to end as well: `POST /api/articles` with a tag list, then `GET /api/tags`, then the rows
read straight out of `psql`, then a genuine process restart — tags survived, articles did not, which is
exactly the half-swapped state.

What it cost:

| | Before | After |
| --- | --- | --- |
| `sbt test` | 4 s | 5 s |
| suite parallelism | on | **off** — `Test / parallelExecution := false` |
| `sbt run` | nothing | needs `docker compose up -d` |

The parallelism is the real price, and it is H8 arriving on schedule. A `Ref`-backed fixture was empty
because it allocated its own `Ref`; a database-backed one has to be *made* empty, and dropping and
rebuilding a shared schema cannot happen in two suites at once. One second on five tests says little —
the number to watch is the same measurement after `articles`.

Two things worth recording:

- **`TagRegistry.postgres` needs no read before its write.** The in-memory version kept a `Vector` and
  called `.distinct`; the table has `name text PRIMARY KEY` and the insert says `ON CONFLICT DO NOTHING`.
  R1.2 is carried by a constraint instead of by a fold, and `TagService` is byte-for-byte the same object
  above either one. That is the cleanest evidence so far that the statement described behaviour.
- **R1.2's test is order-sensitive even though R1.2 is not.** It asserts `after == before` on two
  `List[String]` reads. The query is the naive `SELECT name FROM tags` with no `ORDER BY`, so it passes
  on physical row order rather than on any guarantee — nothing moved between the two reads. This is H1 in
  miniature, and it is latent rather than fixed. Left as it is on purpose: inventing an `ORDER BY` now
  would remove the evidence before `articles` gets to produce the same finding at full scale.

### `users` — specs neutral, two test arrangements changed

All 32 requirements pass against PostgreSQL. No statement touched, no assertion touched — but **two rows
had to be rearranged**, which is outcome three arriving exactly where H2 said it would.

What H7 predicted wrong: the three tests that "write to storage directly" go through the
`UserRepository` algebra, not through a `Ref`. Swapping the implementation under them changed nothing.
R1.8 still reads the row back and proves the password was never stored in the clear — now against a real
table, which is a stronger claim than it used to be.

What H2 got right, and worse than predicted. `TokenIssuer.subjectOf` checks expiry against
`Clock[F].realTime`, and its scaladoc says why: *"so that R5.2 is testable under TestControl instead of
by waiting"*. Production code shaped by a test strategy — and the swap invalidated the strategy. Virtual
time never completes a real socket read, so `TestControl` threw `NonTerminationException`.

Both rows now issue a token that is **already past its expiry** (`tokenTtl = -1.hour`) instead of aging
one two hours under virtual time. The assertion is unchanged in both; only the arrangement moved. The
rewrite was mutation-tested before being trusted: deleting the expiry filter from `TokenIssuer` fails
R5.2 and S1, and nothing else.

That is the honest shape of this result. The specification was neutral. The coupling was never
spec-to-implementation — it was **test-strategy-to-production-design**, and no gate in this repository
was ever going to see it.

#### A harness bug the swap exposed

The first failure cascaded: one `TestControl` row failed, and then *every* later test failed with
`Schema "public" does not exist`. `TestDatabase.fresh` reset the schema with `DROP SCHEMA public CASCADE`
followed by `CREATE SCHEMA public`, and the abandoned program died between the two. Nothing recreated it.

Both statements now carry `IF EXISTS` / `IF NOT EXISTS`. Worth recording because it had nothing to do
with `TestControl` — a cancellation or a timeout would have done the same, and the symptom pointed at
every test except the one that caused it.

#### H11 — a unique violation has nowhere to go · not yet fixed

`users` now has `UNIQUE` on both email and username. They are not what rejects a duplicate:
`UserService.ensureAvailable` checks first, because R1.2 and R1.3 require the *field* to be named, and it
names both at once when both clash. Verified end to end — duplicate email returns 409
`{"errors":{"email":["has already been taken"]}}`, and a direct `INSERT` bypassing the service is refused
by `users_username_key`.

The gap is concurrency. Two simultaneous registrations for one email both pass the check, and the second
hits the constraint — which surfaces as a `SkunkException`, not a `UserError`, so S2's envelope never
gets a chance and the caller sees a 500 instead of a 409. Mapping `23505` back to the right field needs
an error channel the repository algebra does not have: `save` returns `F[Unit]`.

Left unfixed on purpose. H3 forces those signatures open anyway when transactions arrive, and writing
untestable error-mapping code before then would be guessing. **Untested and reasoned, not verified** —
no test in this repository produces a race.

| | Before `tags` | After `tags` | After `users` |
| --- | --- | --- | --- |
| `sbt test` | 4 s | 5 s | 6 s |

## What must not be done

Widening a statement to fit what the database made convenient. `AGENTS.md` already forbids it, and this
experiment is the case the rule was written for. If a statement no longer holds, that is the finding —
report it as drift and stop, rather than editing the spec and reporting success.
