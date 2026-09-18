---
name: sdd4j-scala-typelevel
description: SDD4J stack adapter for Scala 3 and the Typelevel ecosystem. Use when driving Spec-Driven Development in a Scala 3 project with cats-effect, http4s, fs2, circe or Skunk, when a capability spec must live in package.scala rather than package-info.java, or when munit test names must carry EARS requirement ids. Trigger on sdd4j with Scala, spec-driven Scala, package.scala specs, SpecTraceSuite, requirement traceability in munit, or Typelevel spec-driven development.
metadata:
  type: stack
---

# Skill: SDD4J for Scala 3 + Typelevel

## Objective

Fill the **stack** slot of the SDD4J family for Scala 3, and carry the binding overrides that make the
Java-shaped parts of `sdd4j` and `sdd4j-bce` work in a language with no `package-info.java` and no JUnit.

```text
sdd4j                 workflow: setup · new · apply · verify, spec format, EARS, the gap loop
sdd4j-bce             architecture: capability → boundary/control/entity business component
sdd4j-scala-typelevel stack: Scala 3 idioms, sbt, munit, and the overrides below
```

Compose with `sdd4j-ears-tests` for the EARS-group-to-suite transform, and with `cats-effect-io`,
`cats-effect-resource`, `cats-mtl-typed-errors`, `scala-kindlings-derivation` for implementation idioms.

This skill is derived from one complete build: a RealWorld backend, 118 EARS requirements across five
business components, verified against an external conformance suite, then migrated from in-memory
storage to PostgreSQL without a requirement statement changing. Everything below that reads like a rule
is there because something went wrong without it. `references/pitfalls.md` has the evidence.

## Overrides

`sdd4j` and `sdd4j-bce` are written for Java. Where they and this skill disagree, **this skill wins**,
and the project's `AGENTS.md` must say so in as many words.

| `sdd4j` / `sdd4j-bce` (Java) | Scala 3 |
| --- | --- |
| `<component>/package-info.java`, `///` JEP 467 lines | `<component>/package.scala`, one `/** … */` scaladoc on the package clause |
| system doc at `<base>/package-info.java` | `<base>/package.scala` |
| boundary op → public Java method | method on `trait <Bc>[F[_]]` in `boundary`, plus the route that exposes it |
| `## Entities` → entity-layer classes | `opaque type`, `enum`, `case class` in `entity` |
| `@ParameterizedTest` + `@MethodSource` | `RequirementSuite`, one row per EARS statement |
| `{Bc}Requirement` enum, display form `R1.2` | the literal string `"R1.2 — …"` opening the munit test name |
| JUnit runner display names | munit test names, built from the id in exactly one place |

## What `setup` must write

Into `AGENTS.md`, plus `CLAUDE.md` containing only `@AGENTS.md`:

```markdown
Spec source:
- format: `package.scala` — a Markdown scaladoc comment on the package clause
- source root: `src/main/scala`
- requirements style: EARS
- trace ids: `R<n>.<m>` for capability requirements, `S<n>` for system invariants

Architecture layout:
- skill: `sdd4j-bce`
- base package: `<base>`
- component package pattern: `<base>.<component>`
- test package mirrors main package: true, under `src/test/scala`

Stack:
- skill: `sdd4j-scala-typelevel`
- build tool: sbt
- verification command: `sbt test`

Traceability:
- a requirement id is traced by being the **literal prefix of a munit test name**
- `SpecTraceSuite` enforces the trace in both directions and fails `sbt test` on a gap or an orphan
- scaladoc, comments, and test-class names alone do not count

Non-spec packages — explicit adapter exceptions, declared so they are not read as drift:
- `<base>.support.*` — transport bootstrap, configuration, envelopes. No capability behaviour.
- `<base>.Main` — entry point and `Resource` wiring.
```

Into `build.sbt`:

```scala
scalacOptions ++= Seq(
  "-deprecation", "-feature", "-unchecked", "-Wunused:all", "-Wvalue-discard",
  // Capability spec files hold nothing but the spec scaladoc and the package clause. Deliberate.
  "-Wconf:msg=is defined in the compilation unit:s"
)
```

Into `.scalafmt.conf` — **both settings, or requirement statements get reflowed and the baseline gate
reports changes nobody made**:

```
docstrings.style = keep
docstrings.wrap = no
```

Copy from `references/scaffold/`, replacing `«base»` with the base package:

| File | Goes to |
| --- | --- |
| `RequirementSuite.scala` | `src/test/scala/<base>/` |
| `SpecTraceSuite.scala` | `src/test/scala/<base>/` |
| `spec-baseline.sh` | `scripts/`, then run `scripts/spec-baseline.sh record` |

## The spec carrier

A capability spec file contains the scaladoc comment and the `package` clause, **and nothing else**. Not
a helper, not a constant, not a type alias. Two consequences to configure for, both handled above: the
compiler warns that no class is defined in the compilation unit, and scalafmt will reflow the statements
unless told not to.

Never write a second spec for one capability anywhere else. The system doc at `<base>/package.scala`
holds the charter, `## Components` wiring, `## System invariants` (`Sn`), the ubiquitous language, and
the `Dn` decision records.

## Traceability

One suite per `### Rn` requirement group, one row per statement. The test name is built from the id in
exactly one place — `RequirementSuite` — so the trace the gate checks cannot drift from the name the
runner prints:

```scala
class RegisterUserSuite extends RequirementSuite[UsersFixture]:

  protected def setting: IO[UsersFixture] = UsersFixture()

  requirements(
    (
      "R1.2",
      "rejects a duplicate email as a conflict naming the email field",
      fixture =>
        for
          _ <- fixture.register("jake", "jake@jake.jake")
          error <- rejected(fixture.users.registerUser(RegisterUser("other", "jake@jake.jake", "pw")))
        yield assertEquals(error, UserError.Conflict(NonEmptyChain.one(FieldError.taken("email"))))
    )
  )
```

`setting` is re-arranged for every row, so no row can observe another's writes.

## The two gates

**`SpecTraceSuite` — ids.** Every requirement of an applied capability has a test carrying its id; every
id a test claims exists in its own capability's spec. A capability with a spec and no tests is reported
as *pending* rather than failed, so an unapplied capability stays visible without holding the build red.

> **Requirement ids are unique per capability, not globally.** `users` R1.1 and `tags` R1.1 are
> different statements. A gate that matches ids across the whole test tree is blind: any capability's
> R1.1 satisfies every other capability's R1.1. The shipped suite scopes each spec's traces to the test
> directory mirroring its own package. This was a real bug, green for two sessions, found only by
> mutation. See `references/pitfalls.md`.

**`spec-baseline.sh` — wording.** `SpecTraceSuite` says nothing about the *words*. A statement can be
reworded under an unchanged id — same trace, different promise — and the build stays green. That is the
edit a refactor tempts you into. The script hashes each statement's normalised text against a recorded
baseline; whitespace is normalised so re-wrapping is not a change, but one altered word is.

Run it manually, not from `sbt test`: a spec change is legitimate during `new`, and a build gate that
punishes it would be wrong. Re-record with `record` after a deliberate change.

**Mutation-test both gates before trusting them, and after any change to them.** A gate you have not
tried to fool is decoration. The minimum set:

| Mutation | Expected |
| --- | --- |
| Add a statement to a spec, no test | `SpecTraceSuite` fails: untraced requirement |
| Rename an id in a test to one no spec declares | `SpecTraceSuite` fails: orphan |
| Rename capability A's id to one capability B declares | `SpecTraceSuite` fails — if it passes, traces are global |
| Reword a statement's first line | baseline diff |
| Reword only a continuation line | baseline diff |
| Re-wrap a statement without changing words | **no** diff |

## Layer rules

- `entity` — domain state, value types, invariants, and the component's error `enum`. **No effects.**
- `control` — use cases and policies. Requires `Raise[F, <Bc>Error]`. Owns repository algebras and their
  implementations.
- `boundary` — the `<Bc>[F[_]]` trait, its routes, its codecs, and the translation of the component's
  error type into transport statuses. The only layer that closes the typed error scope with
  `Handle.allow` / `rescue`, and the only layer that knows an HTTP status.
- A component reaches another only through that component's `boundary` trait, and only along an edge
  declared in the system doc's `## Components`.

**The architecture constrains storage too, not just packages.** No join across two components' tables,
and no foreign key from one component's table to another's. Referential integrity between components is
the code's job. Say this in the schema's own comments, because the next person will reach for the
foreign key.

## Effect discipline

- Keep `F[_]` polymorphic in `control` and `entity`. `IO` appears only in `Main` and in tests.
- Suspend every side effect. No `Instant.now()`, `UUID.randomUUID()`, or a Java library reading its own
  clock, outside `Clock` / `Random` / `Sync`.
- Prefer `Resource` for anything with a lifecycle, and constructor-inject the result.
- `Sync[F]` does **not** imply `Concurrent[F]`. An assembler that needs both wants `Async[F]`; two
  separate context bounds make givens ambiguous.
- Object initialisation is source-ordered. A `val` referencing a `val` declared below it is `null` at
  that moment, and in a config object that surfaces as a runtime `NullPointerException` no test catches.

## `apply` is not done until

1. `sbt test` green, including both gates.
2. `scripts/spec-baseline.sh` clean, or the statement change is deliberate and re-recorded.
3. No compiler warning under `-Wunused:all`.
4. The external conformance suite passes, if the domain has one.
5. Every new gate or rewritten test has been mutation-tested — break the thing it guards, confirm exactly
   the intended rows fail, restore.

Never widen a spec to bless code that already exists. Surface it as drift and ask.

## Failure modes this skill exists to prevent

Read `references/pitfalls.md` before `apply` on an unfamiliar codebase. In short:

- **A gate matching ids globally.** Silently satisfied by any capability. Mutation-test for it.
- **Production code shaped by a test strategy.** The tell is a comment in main code naming a test
  mechanism. It reads as a design note and is a coupling no spec gate can see.
- **An assertion resting on a property nothing promised** — a collection's insertion order, a table's
  physical row order. It passes until the storage changes, then fails for reasons unrelated to the
  requirement.
- **A test passing for the wrong reason.** When a hazard you predicted does not fire, measure why before
  concluding it is absent.
- **Declared non-spec packages are a blind spot.** Nothing traces them, so nothing tests them. That is
  the correct trade, but know where the gate is silent.
- **Prose documentation losing to the executable suite.** Where an external conformance suite exists, it
  is the oracle; read it before authoring statements, and record that as a `Dn` decision.

## References

| File | What it holds |
| --- | --- |
| `references/pitfalls.md` | Each failure mode above, with the evidence that produced it |
| `references/scaffold/` | `RequirementSuite`, `SpecTraceSuite`, `spec-baseline.sh`, parameterised on `«base»` |
| `references/capability-spec-template.scala` | A `package.scala` capability spec |
| `references/system-doc-template.scala` | A `package.scala` system doc |
