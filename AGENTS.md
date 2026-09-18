# AGENTS.md

A [RealWorld](https://docs.realworld.show/introduction/) backend built with Scala 3 and the Typelevel
stack, developed with SDD4J. The specifications are the contract; the code converges to them.

## SDD4J

Spec source:
- format: `package.scala` — a Markdown scaladoc comment on the package clause (see *Scala realization*)
- source root: `src/main/scala`
- requirements style: EARS
- trace ids: `R<n>.<m>` for capability requirements, `S<n>` for system invariants

Spec language:
- default: `en`

Architecture layout:
- skill: `sdd4j-bce`
- scope: primary project architecture, no exceptions or routing rules
- base package: `realworld`
- component package pattern: `realworld.<component>`
- boundary package: `boundary`
- control package: `control`
- entity package: `entity`
- test package mirrors main package: true, under `src/test/scala`

Stack:
- Scala 3 + Typelevel: cats-effect, cats-mtl, fs2, http4s (ember), circe, Kindlings
- build tool: sbt
- verification command: `sbt test`

Traceability:
- a requirement id is traced by being the **literal prefix of a munit test name**:
  `test("R1.2 — rejects a duplicate email") { … }`
- `SpecTraceSuite` enforces the trace in both directions and fails `sbt test` on a gap or an orphan
- scaladoc, comments, and test-class names alone do not count

Non-spec packages — explicit `sdd4j-bce` adapter exceptions, declared so they are not read as drift:
- `realworld.support.*` — HTTP bootstrap, route composition, JSON envelopes, configuration. Carries no
  capability behaviour and no business rule.
- `realworld.Main` — application entry point and `Resource` wiring.

## Scala realization of `sdd4j-bce`

`sdd4j-bce` is written for Java. This is the binding mapping for this repository; when the skill and this
section disagree, this section wins.

| `sdd4j-bce` (Java) | This project (Scala 3) |
| --- | --- |
| `<component>/package-info.java`, `///` JEP 467 lines | `<component>/package.scala`, one `/** … */` scaladoc on the package clause |
| system doc at `<base>/package-info.java` | `realworld/package.scala` |
| boundary op → public Java method | boundary op → a method on `trait <Bc>[F[_]]` in `boundary`, plus the http4s route that exposes it |
| `## Entities` → entity-layer classes | `## Entities` → types in `entity`: `opaque type`, `enum`, `case class` |
| `@ParameterizedTest` + `@MethodSource` | `List(cases).foreach { c => test(s"${c.id} — ${c.name}") { … } }` |
| `{Bc}Requirement` enum, display form `R1.2` | the literal string `"R1.2 — …"` opening the munit test name |

Spec file rules:
- A capability spec file contains **nothing but** the scaladoc comment and the `package` clause. The
  resulting "No class, trait or object is defined in the compilation unit" warning is suppressed in
  `build.sbt` on purpose.
- Never place code in a spec file, and never place a second spec for one capability anywhere else.
- `scalafmt` is configured with `docstrings.style = keep` and `docstrings.wrap = no` so it cannot reflow
  requirement statements. Do not change those two settings.

Layer rules:
- `boundary` — the component's contract: the `<Bc>[F[_]]` trait, its http4s routes, its JSON codecs, and the
  translation of the component's error type into HTTP statuses. This is where `Handle.allow`/`rescue` closes
  the typed error scope.
- `control` — use cases and policies. Requires `Raise[F, <Bc>Error]`. Owns repository algebras and their
  `Ref`-backed implementations.
- `entity` — domain state, value types, invariants, and the component's error `enum`. No effects.
- A component reaches another component only through that component's `boundary` trait, and only along an
  edge declared in the system doc's `## Components`.

## Working agreements

- Read the capability spec before touching a component. It is the contract, not documentation.
- Never widen a spec to bless code that already exists. Surface it as drift and ask.
- A new behaviour means `/sdd4j new <capability>` first, then `/sdd4j apply <capability>`.
- SLDD is deliberately not used here — see decision D6 in the system doc. Do not create `.sldd/`.
- Keep `F[_]` polymorphic in `control` and `entity`; `IO` appears only in `Main` and in tests.
- Suspend every side effect; no `Instant.now()` or `UUID.randomUUID()` outside `Clock`/`Random`/`Sync`.
- Prefer `Resource` for anything with a lifecycle, and constructor-inject the result.

## Skills

Compose these; they are installed under `.claude/skills/` and mirrored in `.agents/skills/`.

| Skill | Use for |
| --- | --- |
| `sdd4j` | the workflow: `setup` · `new` · `apply` · `verify` |
| `sdd4j-bce` | capability → business component mapping, read through the table above |
| `sdd4j-scala-typelevel` | the stack adapter this project's conventions were distilled into — the same overrides as the table above, plus the failure modes that produced them |
| `sdd4j-ears-tests` | EARS group → table-driven suite, statement → labelled row |
| `cats-effect-io` | effect suspension, `Clock`, `Random`, blocking calls, `TestControl` |
| `cats-effect-resource` | `Resource`-returning factories and constructor injection |
| `cats-mtl-typed-errors` | `Raise[F, E]` in `control`, `Handle.allow`/`rescue` in `boundary` |
| `scala-kindlings-derivation` | circe codecs; never import `io.circe.generic.*` |
| `simplify` | a refinement pass over freshly written code |
| `code-review` | reviewing a change before it lands |
| `conventional-commit` | commit messages |

## Commands

```bash
sbt compile          # compile
sbt test             # verification command — includes the SpecTraceSuite drift gate
sbt run              # start the server on http://localhost:8080
sbt scalafmtAll      # format
scripts/api-conformance.sh   # official RealWorld Hurl suite (see README)
```
