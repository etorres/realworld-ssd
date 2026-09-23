# RealWorld — Scala 3 · Typelevel · Spec-Driven

<!-- sdd4j:generated:start — projection of the specs; do not edit; regenerate from the system doc + per-capability package docs -->

> Serve the RealWorld API contract: a social blogging backend where readers become authors, follow one
> another, and curate what they read by tag.

**Vision:** Make the specification, not the code, the thing you edit first — and let the build tell you
the moment the two disagree.

## Capabilities

| Capability | Responsibility | Spec |
| --- | --- | --- |
| **users** | Own account identity: registration, credential authentication, token issuance, and the account view that only its owner may read. | [`package.scala`](src/main/scala/realworld/users/package.scala) |
| **profiles** | Publish the public face of an account and own who follows whom. | [`package.scala`](src/main/scala/realworld/profiles/package.scala) |
| **articles** | Own authored posts: publishing them under a stable slug, browsing and filtering them, and recording who favorited what. | [`package.scala`](src/main/scala/realworld/articles/package.scala) |
| **comments** | Own the replies attached to an article and who may withdraw them. | [`package.scala`](src/main/scala/realworld/comments/package.scala) |
| **tags** | Own the set of tags in use across the system, so readers can discover what is being written about. | [`package.scala`](src/main/scala/realworld/tags/package.scala) |

## Components

```mermaid
flowchart LR
  profiles --> users
  articles --> users
  articles --> profiles
  articles --> tags
  comments --> users
  comments --> profiles
  comments --> articles
```

<!-- sdd4j:generated:end -->

## The idea

This repository is an experiment in **Spec-Driven Development**, applied to a problem with a real,
externally-defined contract: the [RealWorld](https://docs.realworld.show/introduction/) backend API.

The specification is not documentation *about* the code. It is a `package.scala` file sitting in the same
directory as the code it governs, holding EARS requirement statements with stable ids, and every one of
those ids is carried by a test name. Nothing enforces that by convention — `SpecTraceSuite` enforces it by
failing the build:

```
spec trace coverage (118 ids claimed by tests)
  system       3/3       traced
  articles     48/48     traced
  comments     14/14     traced
  profiles     16/16     traced
  tags         5/5       traced
  users        32/32     traced
```

Add a requirement to a spec and the build goes red until a test carries its id. Write a test claiming an id
no spec declares and the build goes red too, and reports it as drift rather than quietly widening the spec
to match. That second direction is the one that matters: it is what stops the specification from becoming
a story told after the fact.

The background reading that started this — an analysis of Nicolas Duminil's foojay article on living
specifications, and how its Java-shaped ideas carry over to Scala — is in
[`docs/sdd-background.md`](docs/sdd-background.md).

The specifications have since survived a full storage swap, in-memory to PostgreSQL, without a single
statement changing. What did not survive is written up honestly in
[`docs/postgres-swap.md`](docs/postgres-swap.md).

## Status

All five capabilities are applied end to end over http4s with in-memory storage: accounts and tokens,
public profiles and the follow graph, the tag registry, the whole article surface — publishing, browsing
with filters and pagination, the feed, updates, deletion and favorites — and comments.

The [official RealWorld Hurl suite](https://github.com/realworld-apps/realworld/tree/main/specs/api) is the
project-level definition of done, and it passes:

```
Executed files:    13
Executed requests: 154
Succeeded files:   13 (100.0%)
Failed files:      0 (0.0%)
```

`scripts/api-conformance.sh` fetches and runs it against a locally running server. It needs `hurl` on the
path (`brew install hurl`) and a server started with `sbt run`.

## How a capability gets built

```
/sdd4j new <capability>       author or extend the spec in <capability>/package.scala
/sdd4j apply <capability>     converge code and traced tests onto it
/sdd4j verify <capability>    read-only conformance and drift report
```

`AGENTS.md` holds the project configuration: the SDD4J settings, and the mapping that makes the Java-shaped
`sdd4j-bce` adapter work in Scala. Read it before touching a component.

## Conventions

These are project-local standards. They are *declared, not verified* — no requirement id, no test.

- The spec is the contract. Never widen one to bless code that already exists; report it as drift.
- A capability spec file holds the scaladoc and the `package` clause, nothing else.
- Requirement ids are unique per capability, not globally: `users` R1.1 and `tags` R1.1 are different
  statements, and a trace only counts inside its own capability's tests.
- `control` and `entity` stay polymorphic in `F[_]`. `IO` appears only in `Main` and in tests.
- Domain errors travel in a Cats MTL typed channel; `boundary` is the only layer that knows an HTTP status.
- Every side effect is suspended. No `Instant.now()` or `UUID.randomUUID()` outside `Clock`/`Random`/`Sync`.
- Where the RealWorld prose documentation and the Hurl suite disagree, the suite wins (decision D9).

## Build and run

```bash
sbt test           # verification command — includes the SpecTraceSuite drift gate
sbt run            # http://localhost:8080
sbt scalafmtAll    # format
```

Configuration is read from the environment, all of it optional:

| Variable | Default |
| --- | --- |
| `REALWORLD_HOST` | `0.0.0.0` |
| `REALWORLD_PORT` | `8080` |
| `REALWORLD_JWT_SECRET` | a development placeholder — set this for anything real |
| `REALWORLD_TOKEN_TTL_MINUTES` | `1440` |
| `REALWORLD_BCRYPT_LOG_ROUNDS` | `10` |
| `REALWORLD_NODE_ID` | `1` — distinguishes the article-id generators of servers running at once |
| `REALWORLD_DB_HOST` | `localhost` |
| `REALWORLD_DB_PORT` | `5432` |
| `REALWORLD_DB_USER` | `realworld` |
| `REALWORLD_DB_PASSWORD` | `realworld` |
| `REALWORLD_DB_NAME` | `realworld` |
| `REALWORLD_DB_POOL_SIZE` | `8` |

## Storage

PostgreSQL, through Skunk (decision D11). Every capability, and nothing is forgotten on restart. The
`Ref`-backed implementations from decision D3 are still there beside the new ones — the swap was an
experiment in whether the specifications were implementation-neutral, and keeping both is what makes it
a comparison.

```bash
docker compose up -d    # the database sbt run expects
sbt run
```

`sbt test` does not use that database — it starts its own through Testcontainers, which needs only a
running Docker engine. If discovery fails with *client version 1.32 is too old*, that is Testcontainers'
default API version rather than anything local; `TestDatabase` pins a newer one.

Tests no longer run suites in parallel. A database-backed fixture resets a shared schema, and two suites
doing that at once would see each other's tables disappear. The cost is tracked in
[`docs/postgres-swap.md`](docs/postgres-swap.md).

## The storage swap

Decision D3 chose in-memory storage so it could be swapped later. The second experiment asked whether
the specifications were describing behaviour or describing the implementation.

**All five capabilities now run on PostgreSQL, and not one of the 118 requirement statements changed.**
Three tests did: they drove `TestControl`, and virtual time cannot complete a socket read.

The swap also found an ordering hazard that no test could see — articles sharing a creation instant were
ordered by whatever storage returned. `ArticleId` is now a TSID (decision D12), so the identity carries
the order instead of borrowing it.

![The storage-swap experiment](docs/experiment-figure.png)

*Reading figure, portrait. For slides there is a landscape 16:9 version, [light](docs/experiment-slide.png) and [dark](docs/experiment-slide-dark.png).*

[`docs/postgres-swap.md`](docs/postgres-swap.md) was written before the swap started — the surface, the
hazards, and the three possible outcomes fixed in advance so the result could not be decided afterwards
by whoever wrote it up. The results are appended to it, including the hazard that passed for the wrong
reason.

```bash
scripts/spec-baseline.sh    # 118 requirement statements, unchanged since the baseline
```

`SpecTraceSuite` guards the set of requirement ids. This guards their wording, which is the part a
storage swap would be tempted to adjust.
