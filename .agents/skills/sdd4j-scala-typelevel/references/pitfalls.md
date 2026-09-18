# Failure modes, with the evidence

Every item here came out of one project: a RealWorld backend in Scala 3, 118 EARS requirements across
five business components, checked against an external conformance suite, then migrated from `Ref`-backed
in-memory storage to PostgreSQL. The rules in `SKILL.md` are the short form. This is why they exist.

---

## 1. A drift gate that matches ids globally is blind

**What happened.** The first `SpecTraceSuite` collected every `Rn.m` in the spec tree and every `Rn.m` in
the test tree and compared the two sets. It reported `118/118 traced` and had done so for two sessions.

Requirement ids are unique *per capability*. `users` R1.1 and `tags` R1.1 are unrelated statements. With
a global match, any capability's R1.1 satisfies every other capability's R1.1.

**How it was found.** Not by reading it. By mutation: renaming `tags` R1.1 to R1.9 in the spec, which
should have produced one untraced requirement and one orphan trace. The build stayed green —
`Passed: Total 54, Failed 0`.

**The fix.** Scope each spec's traces to the test directory mirroring its own package. System invariants
(`Sn`) belong to no capability, so they are traced by the suites sitting directly under the test root.

**The general rule.** A gate you have not tried to fool is decoration. Mutation-test on the day you write
it, and again after any change to it. The cost is minutes; this one had been weakening two capabilities
silently.

---

## 2. Production code shaped by a test strategy

**What happened.** A token issuer checked expiry against the effect's clock rather than the JWT library's
system clock, and said why in its own scaladoc:

```scala
def subjectOf(token: AuthToken): F[Option[UserId]] =
  Clock[F].realTime.map: now =>
    // Expiry is checked against the effect's own clock rather than jwt-scala's system clock, so that
    // R5.2 is testable under TestControl instead of by waiting.
```

That is main code arranged so a test could use virtual time. When storage moved to PostgreSQL,
`TestControl` could no longer run the test — virtual time never completes a real socket read, so it
deadlocks rather than failing — and the design decision went with it.

Three tests had this shape. Two about token expiry, one asserting an update advances a timestamp, whose
comment claimed the clock had millisecond resolution. It has microsecond resolution. Nothing had ever
tested the belief.

**The fix.** Arrange the condition directly instead of travelling to it. A token issued with a negative
TTL is already expired; two clock reads either side of a real round trip are already apart. Same
assertions, no virtual clock.

**The general rule.** This is a coupling pointing the wrong way — implementation shaped by test, not
specification describing implementation — and **a spec-to-test trace is orthogonal to it**. `118/118`
says nothing about it. The tell is a comment in main code that names a test mechanism. Treat one as a
finding.

---

## 3. An assertion resting on a property nothing promised

**What happened.** Articles are listed newest first. The service sorted by creation time truncated to the
millisecond, then relied on Scala's sort being stable over whatever order the repository returned — and
the in-memory repository returned insertion order. Nothing in the algebra promised that.

Measured, ten articles published back to back in memory:

```
…46.644556Z  …46.654643Z  …46.654932Z  …46.655037Z  …46.655153Z
…46.655259Z  …46.655375Z  …46.655472Z  …46.655580Z  …46.655676Z

three distinct milliseconds; nine of the ten share one with another article
```

The ordering tests passed only because they publish three and five articles while the JIT is still cold,
which spaces them just far enough apart.

**The fix.** Put the ordering property in the domain rather than borrowing it. The identifier became a
TSID — a 64-bit id whose high bits are the millisecond it was minted in — so both the in-memory and the
SQL implementation order by the same fields and agree by construction. Two regression tests force the
tie and then disturb the storage.

**The general rule.** When an assertion depends on order, ask what guarantees it. "The collection happens
to preserve insertion order" is not a guarantee, and it will survive exactly until the storage changes.

---

## 4. A test can pass for the wrong reason

**What happened.** The ordering hazard above was predicted in writing before the storage swap, along with
the plan to swap naively first and watch the test fail. It did not fail. Everything passed on the first
run.

Measuring before concluding: the same ten publishes through PostgreSQL land **12 to 66 milliseconds
apart**, because each one now costs several round trips. The database is slow enough that the collisions
stopped happening. *The latency the swap cost is what made the ordering assertion pass.* Nothing about
correctness improved.

And forcing the tie that the clock was hiding — four rows sharing a creation instant exactly:

```
three repeated reads:    alpha, bravo, charlie, delta
after rewriting one row: bravo, charlie, delta, alpha
```

Ties resolve by physical position in the table, and an update moves a row to the end of it. The hazard
was not fixed. It was worse than before, and invisible.

**The general rule.** When a hazard you predicted does not fire, find out why before recording it as
absent. Had the fix been applied up front, the suite would have been green and the obvious reading —
"the specifications were neutral and the ordering carried over" — would have been false.

---

## 5. Declared non-spec packages are where the gate is silent

**What happened.** Adding database settings to the configuration object introduced a `val` referencing a
`val` declared below it. Scala initialises object fields in source order, so the second was `null` when
the first captured it, and the application died at startup with a `NullPointerException` from inside the
config library.

`sbt test` stayed green throughout. The configuration package is a declared non-spec adapter exception —
no requirement traces it, no test loads it — so the gate is silent there by construction.

**The general rule.** Declaring non-spec packages is right; it is what stops `support` and `Main` reading
as drift. But know that it buys the silence. `118/118 traced` is a statement about the capabilities and
about nothing else, which is easy to forget when the report is that tidy.

---

## 6. The executable suite outranks the prose

**What happened.** The domain shipped both prose endpoint documentation and an executable conformance
suite. They disagreed eleven times — on blank-field wording, on whether a duplicate is a conflict or a
validation failure, on password length bounds.

Reading the suite *before* authoring the requirement statements caught all eleven before any code
existed. Reading the prose first would have produced eleven statements that were wrong, each faithfully
implemented and traced, with a green gate.

**The general rule.** Where an external suite exists, it is the oracle, and that belongs in the system
doc as a `Dn` decision so the next session does not relitigate it. A gate enforces that code matches the
specification. It has no opinion about whether the specification is right.

---

## 7. Two architectural blockers only a strict boundary reveals

BCE forbids a component reading another's storage. Twice that turned a convenience into a design
question that had to be answered in the spec:

- A component had to publish fields another component owned. It could not read them, so the owner grew a
  boundary operation to report its publicly shareable fields, and the reader composes them with its own
  state.
- A component holding a reference to another's record needed that record rendered. Holding the *name*
  would rot when the name changed, so it holds the identity, and the owner grew an operation that
  resolves an identity rather than a name.

Neither would have surfaced under a layered architecture, where both are one field access away. Both
became `Dn` decisions and new boundary operations.

**The same rule reaches the schema.** No join across two components' tables, and no foreign key from one
component's table to another's — a foreign key is a reference into storage the component does not own.
Referential integrity between components becomes the code's job. This is the first place the
architecture charges something a database gives away free, and the temptation to widen a spec to justify
the shortcut is highest here.

---

## 8. If you plan to test whether the specs are neutral, pre-register

**What happened.** The storage swap was an experiment: do the specifications describe behaviour, or do
they describe the implementation? Before touching anything, the outcomes were fixed in writing.

| Outcome | What it looks like |
| --- | --- |
| Specs neutral | Statements unchanged, traces intact, only fixture construction edited |
| Specs leaked | A statement reworded or retired to fit what the storage made convenient |
| Tests leaked | Specs clean, but a requirement's assertions had to change |

The third was not in the original framing. It appeared while writing the pre-registration, and it is
where the result landed.

**The general rule.** An experiment you grade yourself gets its result decided by whoever writes it up.
Write the conditions down first, and record the measuring instrument — here, the statement-hash baseline,
because the existing gate could not see rewording.

---

## Scala and Typelevel specifics worth knowing up front

| Symptom | Cause |
| --- | --- |
| `No given instance of Concurrent[F]` inside an assembler that has `Sync[F]` | `Sync` does not extend `Concurrent`. Use `Async[F]`; two separate bounds make givens ambiguous |
| `NullPointerException` from a config library at startup | A `val` referencing a `val` declared below it. Object fields initialise in source order |
| A domain type named `Session` colliding with `skunk.Session` | Qualify the library one at its single use site rather than renaming the domain type |
| Requirement statements reflowed by the formatter | `docstrings.style = keep` and `docstrings.wrap = no` are both required |
| `No class, trait or object is defined in the compilation unit` | Expected for spec files. Suppress with `-Wconf:msg=is defined in the compilation unit:s` |
| Testcontainers: `client version 1.32 is too old` | Testcontainers pins that API version. Set the `api.version` system property before the first client is built |
| Tests interfering once storage is shared | A database-backed fixture resets shared state; `Test / parallelExecution := false`. Make the reset self-healing (`DROP … IF EXISTS`, `CREATE … IF NOT EXISTS`), or one abandoned test poisons every later one |
| `TestControl$NonTerminationException` | Virtual time cannot complete real I/O. See item 2 — the test needs rearranging, not the runtime |
