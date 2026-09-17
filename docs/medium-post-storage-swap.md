# I changed the database to test my specifications. My tests broke instead.

*A spec-driven backend, swapped from in-memory storage to PostgreSQL. All 118 requirements survived untouched. Three tests didn't — and the reason they broke is the opposite of the one I was looking for.*

> Carried over from [the first post](https://medium.com/@etserrano/how-my-spec-driven-build-went-green-with-zero-tests-58f71a80cdf7), so the rest can stay short. SDD4J is Spec-Driven Development for Java. EARS is Easy Approach to Requirements Syntax, a template that turns a requirement into one testable statement. A BC is a business component, the unit of code a single specification governs.

---

Last time I built a [RealWorld](https://docs.realworld.show/introduction/) backend in Scala 3 where the specification was a build artifact: 115 EARS requirements across five business components plus three system invariants belonging to none of them, each id carried by a test name, and a gate that turned the build red when the two disagreed.

That post ended on a promise. Every repository sat behind a `Ref`-backed in-memory implementation, chosen explicitly so it could be swapped later. If the specifications really described *behaviour* rather than *my implementation*, moving them onto a real database should touch zero requirement statements. If it forced a spec change, that was the interesting result.

It's done. The specifications survived without a single word changing. That is the least interesting thing I can tell you about it.

## Writing down the answer before doing the work

The problem with an experiment you grade yourself is that the result gets decided by whoever writes it up. Work at it for a while, edit some tests along the way, and by the end you genuinely cannot remember whether that assertion changed because the storage demanded it or because you nudged it.

So before touching anything I wrote a pre-registration: the swap surface, nine hazards found by reading the code, and three outcomes fixed in advance.

| Outcome | What it looks like |
| --- | --- |
| **Specs neutral** | Statements unchanged, traces intact, only fixture construction edited |
| **Specs leaked** | A statement reworded or retired to fit what the database made convenient |
| **Tests leaked** | Specs clean, but a requirement's assertions had to change |

The third one wasn't in the original framing. It appeared while I was writing the pre-registration, from staring at one line of code — and it turned out to be where the whole thing landed.

I also needed a measuring instrument, because the existing gate couldn't do it. `SpecTraceSuite` checks that every requirement **id** has a test and every test id exists in a spec. It has nothing to say about the *words*. A statement can be quietly reworded under an unchanged id — same trace, different promise — and the build stays green. That is exactly the edit a storage swap would tempt you into.

So: a script that hashes each statement's normalised text, recorded as a baseline before the work started. Whitespace is normalised so re-wrapping a line isn't a change; one altered word is. I mutation-tested it before trusting it — reworded an opening line, reworded only a continuation line, retired a statement, and re-wrapped without changing words. The first three produce a diff. The fourth doesn't.

```
$ scripts/spec-baseline.sh
all 118 requirement statements are unchanged since the baseline
```

That command ran after every capability. It is the only reason I can make the claim in the title.

## The result

Five capabilities moved to PostgreSQL through [Skunk](https://typelevel.org/skunk/), one at a time, starting with the smallest.

| | |
| --- | --- |
| Requirement statements changed | **0** of 118 |
| Test assertions modified | **0** |
| Test arrangements rewritten | **3** |
| Tests added | **2** |
| Conformance suite | 13/13 files, 154 requests |
| `sbt test` | 4 s → 8 s |

Main code gained 647 lines across 18 files and lost 45. The test tree gained 244 and lost 56 across 12 files, and most of that is five fixtures learning to build a component over a connection pool instead of a `Ref`, plus the harness that starts the database.

Outcome one for the specifications. Outcome three for three tests. Here's what those three were.

## The tests that broke, and why it's the wrong way round

All three drove `TestControl`, cats-effect's virtual clock. It makes a test that needs two hours to pass run instantly — and it works by simulating every effect in the program. A real socket read to PostgreSQL never completes under simulated time, so it doesn't fail with a wrong answer. It deadlocks.

Two of the three were about token expiry. The third checked that updating an article advances its update time. Here's the token issuer they were testing:

```scala
def subjectOf(token: AuthToken): F[Option[UserId]] =
  Clock[F].realTime.map: now =>
    // Expiry is checked against the effect's own clock rather than jwt-scala's system clock, so that
    // R5.2 is testable under TestControl instead of by waiting.
    JwtCirce.decode(token.value, secret, …, JwtOptions(expiration = false))
```

Read the comment again. I wrote it in the first commit, before any of this. It says, in as many words, that the production code reads the clock this way **so that a test could use virtual time**.

That is not a specification describing an implementation. It is an implementation shaped by a test strategy — and when the storage changed, the strategy stopped working and took the design decision with it.

The fix was smaller than the finding. Instead of ageing a token two hours, issue one that is already past its expiry:

```scala
for
  fixture <- UsersFixture(tokenTtl = -1.hour)
  user    <- fixture.stored("jake", "jake@jake.jake")
  token   <- fixture.tokens.issue(user.id)
  error   <- rejected(fixture.users.authenticateToken(token))
yield assertEquals(error, UserError.invalidToken)
```

Same assertion, no virtual time, and arguably a better test — it exercises the predicate directly instead of travelling to a point where the predicate is true. I mutation-tested each rewrite before trusting it: delete the expiry check from the token issuer and exactly those rows fail, and nothing else.

The third one was the same shape. Its comment claimed the clock had millisecond resolution and so needed a simulated gap. The clock has microsecond resolution, and a database round trip is far wider than that. The comment had been wrong the whole time; nothing had ever tested the belief.

This is the finding I actually care about. I built a gate to catch specifications drifting toward the code. What drifted was the code toward the tests, and **no gate in the repository can see that.** `118/118 traced` says nothing about it.

## The hazard that passed for the wrong reason

The sharpest thing in the pre-registration was an ordering problem I'll call H1.

Articles are listed newest first. The service sorted by creation time truncated to the millisecond, then leaned on Scala's sort being stable over whatever order the repository returned — and the in-memory repository returned insertion order. Nothing promised that. So before starting I measured how thin it was. Ten articles published back to back, in memory:

```
…46.644556Z  …46.654643Z  …46.654932Z  …46.655037Z  …46.655153Z
…46.655259Z  …46.655375Z  …46.655472Z  …46.655580Z  …46.655676Z
```

**The ten landed in three distinct milliseconds; nine of them share one with another article.** The suite only passed because the two tests that check ordering publish three and five articles respectively, while the JIT is still cold and each publish is slow enough to land in its own millisecond. Every assertion about order was resting on a coincidence.

PostgreSQL has no equivalent coincidence. `SELECT … ORDER BY created_at` says nothing about rows with equal timestamps. So the pre-registration was explicit: **swap naively first, with no tie-break, and watch the ordering test fail.** That failure was the measurement.

It didn't fail. Everything passed on the first run.

Before concluding anything, I ran the same ten publishes through the new storage:

```
…20.514331Z  …20.580331Z  …20.608558Z  …20.631243Z  …20.647390Z
…20.660943Z  …20.672909Z  …20.684394Z  …20.696243Z  …20.708443Z

distinct milliseconds: 10 of 10
```

They are **12 to 66 milliseconds apart**. Each publish now costs several round trips — the slug-uniqueness loop, the upsert, the tag registration, the author lookup, the favourites read. The database is slow enough that the collisions stopped happening.

*The latency that the swap cost me is what made the ordering test pass.* Nothing about the ordering got more correct.

Then I forced the tie that the clock was hiding. Four articles sharing a creation instant exactly:

```
three repeated reads:    alpha, bravo, charlie, delta
after rewriting one row: bravo, charlie, delta, alpha
```

Where a tie exists, the order is physical position in the table — and an update moves a row to the end of it. Changing an article's title rewrites its row on every edit.

So the swap left that hazard **worse than it found it**. In memory, ties broke by insertion order: unspecified, but stable. In PostgreSQL they break by heap position, which moves. The test suite cannot see either state.

Had I adopted the fix up front — the sensible-looking thing to do, since I already knew the hazard was there — the suite would have gone green and the obvious reading would have been *"the specifications were neutral and the ordering carried over."* That sentence would have been false, and I would have had no way to know. Pre-registering the naive attempt is the only reason I found out.

## Three things a `Ref` could never have shown me

**An out-of-scope note became rows.** After the conformance suite ran, the database held 16 users, 3 tags, 2 comments and 0 articles. Two comments attached to articles that no longer exist. That is not a bug — the `comments` specification puts it in `## Out of scope`, because `articles` does not call `comments` and so cannot tell them to go. In memory that was a sentence in a doc comment. In a database it is two rows that will still be there next year.

**The architecture constrains the schema, not just the packages.** The `follows` table holds two columns pointing at accounts, and the obvious move is a foreign key to `users(id)`. There isn't one. A reference from one component's table to another's is that component reaching into storage it doesn't own, which the architecture forbids for the same reason it forbids a join across them. Referential integrity *between* components is now the code's job. That cost no requirement and no test, but it is the first point where the design charged me something a database gives away free.

**Configuration has no requirement, so it has no test.** Adding database settings introduced a Scala initialisation-order bug: a `val` referencing another `val` declared below it, which is `null` at that moment. `sbt test` stayed green. The configuration package is declared outside the specification surface — no requirement traces it, no test loads it — so the first symptom was a server that wouldn't start. The gate is silent there by construction, which is easy to forget when the report says 118/118.

## Closing the ordering hazard properly

Once measured, H1 was worth fixing rather than documenting. Three options: rewrite the assertions to accept a tie-break nobody can predict; add a sequence column whose only job is keeping a test green; or give the identity the ordering property it had been borrowing.

I took the third. `ArticleId` went from a random UUID to a [TSID](https://github.com/vladmihalcea/hypersistence-tsid) — 64 bits, the high 42 of which are the millisecond it was minted in, so it sorts by creation order. Within a single millisecond the generator increments a counter instead of re-reading the clock, which is what makes consecutive identifiers ordered rather than merely unique. I verified that on a JVM before adopting it: ten identifiers from one generator, all landing in a single millisecond, strictly increasing.

The part that matters isn't the SQL. It's that **both implementations now order by the same fields**. The in-memory repository yields insertion order, which for TSIDs *is* identifier order. They agree by construction instead of by coincidence, which is what the in-memory version never managed.

Two caveats I wrote down rather than discovered later. Across several servers, identifiers minted in the same millisecond sort by node rather than by which came first — a stable answer, not a true one, which the requirement permits because it says nothing about ties. And I did not extend TSIDs to comment identifiers, which are published as JSON numbers: at around 8.9 × 10¹⁷ they exceed JavaScript's safe integer range by a factor of a hundred, and the conformance suite would not have caught it.

Both orderings now have a test that forces the tie and then disturbs the storage — one rewrites a row, the other deletes a comment and vacuums so the next insert takes the freed slot. Put the old ordering back and exactly those two tests fail, and nothing else does.

## What's still open

Being honest about a result includes the parts still outstanding.

- **Deleting an article and deleting its favourites are two statements on two connections.** The requirement says an article is removed *together with* its favourites. A failure between them now leaves orphan rows in a real table. Fixing it means threading a transaction through the control layer.
- **A unique-constraint violation surfaces as a 500, not a 409.** Two simultaneous registrations for one email both pass the service's check and the second hits the index. The repository has no error channel to translate it.
- **Listing articles still reads the whole table.** A twenty-article page issues one full favourites read per row. Correct, and absurd.

None of those is a specification problem. Two of the three were in the pre-registration before the work started; the constraint violation turned up while swapping accounts, and went into the same list.

## What I'd tell you

The specifications held. 118 statements across five capabilities and the system itself, a different storage engine underneath, not one word changed. If you want the short version, that's a point in favour of writing requirements as behaviour and letting a gate enforce the trace.

The longer version is less comfortable. A green gate measures what the gate measures. Mine checked that every requirement had a test and every test named a real requirement, and it did that honestly the whole way through. It could not see that a production method read the clock a particular way so that a test could travel through time, or that an ordering assertion was resting on a `Vector`'s insertion order, or that the same assertion later rested on the physical layout of a table.

Those are all couplings pointing the wrong way — implementation shaped by test, test resting on storage — and a spec-to-test trace is orthogonal to every one of them.

The experiment was worth running for the eleven contract errors the specifications caught up front, which I wrote about last time. It was worth running a second time for this: the discovery that the thing I built to detect drift was blind to the drift I actually had. Writing down what would count as failure, before starting, is what made that visible instead of tidy.

---

*The code, the specifications, the pre-registration and the results are in [the repository](https://github.com/etorres/realworld-ssd). `docs/postgres-swap.md` is the pre-registration with the findings appended to it, in the order they happened.*
