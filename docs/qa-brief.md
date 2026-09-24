# Q&A brief

Thirteen questions for the talk on 2026-09-25, with the answer to give and the backup facts behind it.
Every number here was checked against the repository. Six were rehearsed; the rest come from slide 22's
presenter notes.

The three most likely to decide whether the room acts are 4, 5 and 6. They are the ones the slide notes
do not cover.

---

## 1. What is the honest overhead, and when does it pay back?

*"You're writing 484 lines of specification and your test suite is 48% of the codebase. My team ships
every two weeks."*

**Say this.** Concede the cost first. 118 traced statements is 484 lines of prose to write and keep
true, and the test tree runs about 1.1 lines per line of implementation. Four times the workflow
stopped and handed a decision back instead of proceeding, and each of those was a round trip.

**Then the return.** The conformance suite passed on the first run: 13 files, 154 requests, no fixes.
That is not luck. It is eleven contract errors arriving as spec questions at the start instead of as a
red build at the end. A two-minute spec edit each, rather than a thirty-minute detour each.

**If pushed on payback.** The overhead is front-loaded and the return is at the point of change. The
database swap is the measurement: a full storage rewrite, and not one requirement statement moved.

---

## 2. Your gate was broken. Why should I trust it, or mine?

*"You only found out because you renamed a test on purpose."*

**Say this.** Do not defend the gate. Concede it immediately: it was matching requirement ids in one
flat global set, ids are only unique within a capability, and `users`, `profiles` and `tags` all
declare an `R1.1`. It quietly weakened two capabilities for two sessions.

**Then the actual argument.** I trust it now because I broke it on purpose five times across the
project: untraced requirement, orphan id, swallowed token, cross-capability collision, platform
timestamp. Each time exactly one thing failed, and it was the right one.

**The line to land.** An unverified verifier is just a comment that compiles. Mutation-test yours.

---

## 3. The repository shows a spec file changed. Which is it?

*"`git log` shows seventeen lines added to `realworld/package.scala` during the swap."*

**Say this.** Go first, before they finish. Yes, seventeen lines, and they are the D11 and D12 decision
records: PostgreSQL through Skunk, and a TSID article identity. Decisions, not requirement statements.

**Why the distinction holds.** `scripts/spec-baseline.sh` hashes each statement's normalised text and
reports all 118 unchanged. Whitespace is normalised so re-wrapping is not a change, but one altered
word is. It was written before the work started and mutation-tested four ways: reword an opening line,
reword a continuation line, retire a statement, re-wrap without changing words. The first three produce
a diff. The fourth does not.

---

## 4. What transfers to a legacy monolith?

*"Greenfield, five components, one developer, and an external oracle telling you when you were wrong."*

**Say this.** Concede all three. RealWorld handed me a conformance suite that could tell me I was wrong,
and most projects have no such thing. Without an oracle a spec-driven loop can converge confidently on
the wrong contract.

**What transfers.** `SpecTraceSuite` is 114 lines and the trace convention is a naming rule, so
the gate goes on one existing package next week without touching the architecture. Start there.

**What does not.** Retrofitting Boundary-Control-Entity onto a monolith is a rewrite wearing a
methodology's clothes. Do not sell it as one.

**The argument for legacy, not against it.** Writing the contract down is what surfaced two design
errors during `apply`: `profiles` was specified to publish a username, bio and image that live in
`users`, which deliberately exposes an account to nobody but its owner. Both specs were internally
coherent. Together they were impossible. On a codebase nobody fully understands, that is the payoff,
not the drift gate.

---

## 5. Is this just prompt engineering?

*"Your write-up says it was built with Claude Code."*

**Say this.** Accept the premise. Yes, an agent wrote much of it. The difference is what survives the
session. A prompt is consumed and gone. `package.scala` compiles, ships inside the artifact, and turns
the build red when a requirement stops having a test. Nobody diffs a prompt against `main` six months
later.

**The evidence.** Four times `apply` stopped and handed a decision back, because in SDD4J `apply`
writes code and is not permitted to write contracts. That separation is what caught the `profiles`
error. An agent with a long prompt papers over that with a convenient shortcut.

**Close on this.** `sbt test` enforces the trace, not the model. The build does not care who typed it.

---

## 6. Doesn't the gate make change expensive?

*"Product changes its mind on Thursday. You've built a ratchet."*

**Say this.** It makes change visible, not expensive. Change happened eleven times in this project,
every one of them a correction from reading the conformance suite before writing the requirements.

**What it costs.** One extra step: `new` before `apply`, contract before code. What that buys is that
nobody can quietly widen a requirement to bless code they already wrote, because `apply` cannot touch
contracts. The working agreement says it outright: never widen a spec to bless code that already
exists, surface it as drift and ask.

**The honest limit.** When a requirement legitimately changes you edit the statement and the test
carrying its id, and the baseline script reports the wording changed. Two files. A ratchet is one where
nobody can change anything. This is one where nobody can change it silently.

---

## 7. Why TSID and not UUIDv7?

Sub-millisecond monotonicity is optional in RFC 9562, so UUIDv7 settles nothing for the case I hit: two
articles created in the same millisecond. A TSID is 64 bits whose high 42 are the millisecond it was
minted in, and within one millisecond the generator increments a counter rather than re-reading the
clock, which is what makes consecutive identifiers ordered rather than merely unique.

Also say what was rejected: a `bigserial` column, which would have fixed only the SQL implementation and
existed purely to keep a test green.

The point is not the SQL. It is that both implementations now order by the same fields. The in-memory
repository yields insertion order, which for TSIDs is identifier order. They agree by construction
instead of by coincidence.

---

## 8. Then why are comment ids still integers?

Because comment ids are published as JSON numbers. A TSID lands around 8.9 × 10^17, which is roughly a
hundred times past JavaScript's safe integer range of 9.007 × 10^15.

The sting worth landing: the conformance suite would not have caught it. That is a caveat I wrote down
rather than discovered later.

---

## 9. Do I need Docker?

Yes, Testcontainers and PostgreSQL. `sbt run` also wants `docker compose up -d`, and
`Test/parallelExecution` is off because the fixtures reset a shared schema.

One gotcha worth ten seconds: Testcontainers asks for Docker API 1.32, engines from Docker 25 onward
refuse anything below 1.40, and the `DOCKER_API_VERSION` environment variable is ignored. The fix is the
`api.version` system property, set before the first client is built.

---

## 10. Did the architecture cost anything real?

One concrete thing. The `follows` table holds two columns pointing at accounts and has no foreign key to
`users(id)`. A reference from one component's table into another's is that component reaching into
storage it does not own, which the architecture forbids for the same reason it forbids a join across
them.

Referential integrity between components is now the code's job. That cost no requirement and no test,
but it is the first point where the design charged me something a database gives away free.

---

## 11. What is still broken?

Three, in order of how much they matter.

**H3.** Deleting an article and deleting its favourites are two statements on two connections. A failure
between them leaves orphan rows, and R6.1 says an article is removed together with its favourites. That
makes it a real correctness defect, not a nit. Fixing it means threading a transaction through the
control layer.

**H11.** A unique-constraint violation surfaces as a 500 rather than a 409. Two simultaneous
registrations for one email both pass the service check and the second hits the index. The repository
has no error channel to translate it. H3 unblocks this one.

**H5.** Listing articles reads whole tables. A twenty-article page issues one full favourites read per
row. Correct, and absurd.

Two of the three were in the pre-registration before the work started.

---

## 12. How is this different from Spec Kit or OpenSpec?

Same conviction, different carrier. GitHub Spec Kit runs a spec-first workflow through slash commands
over a `/specs` directory, aimed at agent-assisted development. OpenSpec treats each change as a
reviewable spec delta, archived once shipped.

SDD4J puts the contract in package metadata beside the code it governs. That co-location is the only
reason a build gate is possible at all, because the specification is inside the compilation unit rather
than beside the repository.

SLDD, Loiane Groner's specs-driven feedback loop, is deliberately unused here. That is decision D6.

---

## 13. Tests are 48% of the codebase. That is not lean.

Correct, and I would not claim otherwise. Measured from the repository: 2,340 lines of application code
at 43%, 2,665 lines of tests at 48%, 484 lines of co-located specification at 9%, out of 5,489 non-blank
lines under `src/`.

Each individual test got leaner, because a test no longer needs a creative name or verbose setup, only
executable proof of one requirement id. The aggregate did not shrink. Roughly 1.1 lines of test per line
of implementation.

The 9% is the number worth defending: 484 lines of specification governing 2,340 lines of
implementation, and it is documentation you would have written anyway, except the build fails when it
stops being true.

---

## Numbers, for reference

| | |
| --- | --- |
| Requirements | 118 total: 115 EARS across five components, plus 3 system invariants |
| Per component | articles 48, users 32, profiles 16, comments 14, tags 5 |
| Tests | 122 |
| Conformance | Hurl 13 of 13 files, 154 requests, first run |
| Swap, specs | 0 of 118 statements changed, 0 assertions modified |
| Swap, tests | 3 arrangements rewritten, 2 tests added |
| Swap, code | main +647 / -45 lines, tests +244 / -56 |
| Suite duration | 4s to 8s |
| Ordering, in memory | ten publishes, three distinct milliseconds, nine sharing one |
| Ordering, PostgreSQL | ten of ten distinct, 12 to 66 ms apart |
