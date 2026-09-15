# My spec-driven build went green while a requirement had no test at all

*Building a RealWorld backend in Scala 3 with SDD4J. The gate caught eleven contract errors before any code existed, and I only found the hole in it by breaking it on purpose.*

> Defined once so the rest can stay short. SDD4J is Spec-Driven Development for Java. EARS is Easy Approach to Requirements Syntax, a template that turns a requirement into one testable statement. A BC is a business component, the unit of code a single specification governs.

---

Every project I've worked on has had specifications that were true on the day they were written.

Writing a good one is the easy half. The hard half is keeping it true three commits later. So I ran an experiment: build a complete backend, checked against an external suite, where the specification is a build artifact. Adding a requirement turns the build red until something proves it. Writing code that no requirement describes turns the build red too.

The target was [RealWorld](https://docs.realworld.show/introduction/), the "Medium clone" API spec that exists so implementations can be compared against each other. It ships an [official conformance suite](https://github.com/realworld-apps/realworld/tree/main/specs/api), which runs under [Hurl](https://hurl.dev/) or Bruno, so I couldn't grade my own homework. The stack was [Scala 3](https://www.scala-lang.org/) and [Typelevel](https://typelevel.org/): [cats-effect](https://typelevel.org/cats-effect/), [http4s](https://http4s.org/), [circe](https://circe.github.io/circe/), [cats-mtl](https://typelevel.org/cats-mtl/). The method was [SDD4J](https://github.com/soujava/agent-skills), which is written for Java.

It ended up as 115 EARS requirements across five business components, plus three system invariants that belong to no single component, and 120 tests. The conformance suite passed 13 of 13 files over 154 requests on the first run. 467 lines of specification govern 2,290 lines of implementation.

The passing suite is the least useful thing I can tell you about it. What I actually got was a list of things the gate caught before any code existed, plus the discovery that the gate had been lying to me for two sessions.

## Making a Java-shaped method speak Scala 3

SDD4J puts a capability's contract in `package-info.java`, sitting in the same directory as the code it governs, written as a Markdown doc comment. Requirements use EARS phrasing ("If the email is already registered, then the BC shall reject the registration as a conflict"), each with a stable id, and each id has to be carried by a test.

Scala 3 has no `package-info.java`. The first job was a binding mapping, written into `AGENTS.md` so every future session reads the same rules:

| SDD4J (Java) | This project (Scala 3) |
| --- | --- |
| `<component>/package-info.java` | `<component>/package.scala`, one scaladoc on the package clause |
| boundary op → public Java method | method on `trait <Bc>[F[_]]`, plus its http4s route |
| `## Entities` → entity classes | `opaque type`, `enum`, `case class` in `entity` |
| `@ParameterizedTest` + `@MethodSource` | `List(rows).foreach { test(s"$id — $name") { … } }` |
| `{Bc}Requirement` enum | the literal string `"R1.2 — …"` opening the test name |

A capability spec file contains the comment and the package clause, and nothing else:

```scala
/** # Tags
  * > Own the set of tags in use across the system.
  *
  * ## Boundary
  * - `register-tags` — add the tags an article declares to the set in use
  * - `list-tags` — return every tag in use
  *
  * ## Requirements
  *
  * ### R1: Register tags in use
  * - R1.1 — When tags are registered, the BC shall add each of them to the set of tags in use.
  * - R1.2 — When a tag that is already in use is registered again, the BC shall leave the set unchanged.
  * - R1.3 — If a registered tag is blank, then the BC shall ignore it. _(why: `articles` should not have
  *   to sanitise before calling)_
  *
  * … R2, Entities and Out of scope omitted
  */
package realworld.tags
```

That produces a compiler warning ("No class, trait or object is defined in the compilation unit"), which I suppress with one narrow `-Wconf` rule. Keeping the spec file free of code was worth a line of build configuration.

Two [scalafmt](https://scalameta.org/scalafmt/) settings matter more than they look: `docstrings.style = keep` and `docstrings.wrap = no`. Without them the formatter reflows your requirement statements. Losing the wording of a requirement to a code formatter would be a particularly stupid way to fail.

## The gate

The trace convention is deliberately dumb. A requirement id is traced by being the literal prefix of a test name, so there's no annotation to apply and no registry to keep in sync:

```scala
abstract class RequirementSuite[Setting] extends CatsEffectSuite:
  protected def setting: IO[Setting]

  protected def requirements(rows: (String, String, Setting => IO[Unit])*)(using munit.Location): Unit =
    rows.foreach: (id, statement, check) =>
      test(s"$id — $statement")(setting.flatMap(check))
```

Because the test name is built from the id and nowhere else, the name the runner prints can't drift from the id the spec declares. A requirement group becomes a table:

```scala
class RegisterTagsSuite extends RequirementSuite[TagsFixture]:
  requirements(
    ("R1.2", "leaves the set unchanged when a tag already in use is registered again",
      fixture =>
        for
          _      <- fixture.tags.registerTags(List("dragons", "training"))
          before <- fixture.inUse
          // Once again in a later call, and twice within a single one.
          _      <- fixture.tags.registerTags(List("dragons"))
          _      <- fixture.tags.registerTags(List("training", "training"))
          after  <- fixture.inUse
        yield
          assertEquals(after, before, "re-registering a tag already in use changed the set")
          assertEquals(after.count(_ == "dragons"), 1)),
    …
  )
```

Then one suite, `SpecTraceSuite`, reads the spec files and the test sources as text and fails the build in both directions. Spec to test: every requirement in an applied capability must appear in that capability's tests, so a new statement with no test is a red build. Test to spec: every id a test claims must exist in its capability's spec, and an orphan gets reported as drift rather than absorbed by widening the spec to match.

That second direction is the one that does the work. It's what stops the specification from turning into a description of whatever the code already does.

Capabilities with no tests yet get reported as pending rather than failed, so an unbuilt capability stays visible without holding the build hostage.

## Eleven things the gate caught before any code

RealWorld has prose documentation and an executable Hurl suite, and the docs themselves say the tests are the source of truth. I wrote that down as a decision, D9: where the suite and the prose disagree, the suite wins and the spec gets corrected. Then I made a habit of reading a capability's Hurl file before authoring its requirements.

They disagreed eleven times. Every one of these arrived as a question about the specification rather than as a failing conformance run at the end.

1. Duplicate email or username returns 409, not 422. The prose lumps everything under "validation failure".
2. The blank-field message is `"can't be blank"`, where the docs say `"can't be empty"`.
3. Passwords have an 8-character floor and must accept at least 64. That's [NIST SP 800-63B](https://pages.nist.gov/800-63-3/sp800-63b.html), and it's invisible in the prose.
4. `bio` and `image` are three-state. Omitted means unchanged; both `""` and `null` mean clear.
5. `email`, `username` and `password` are three-state too, except that for them empty is a rejection rather than a clear.
6. A wrong password returns `credentials: invalid`, deliberately indistinguishable from an unregistered address.
7. A missing token returns `token: is missing`, which the prose never mentions.
8. Listings leave the article body out entirely. Reading a single article includes it. That's two projections, where I had specified one.
9. Tag order is contract. `tagList[0]` and `tagList[1]` are asserted positionally.
10. `PUT /articles/:slug` accepts `tagList`, where omitting preserves, `[]` clears, and `null` is a 422. The whole field was missing from my update operation.
11. Comment ids are integers. A UUID satisfies "unique across every comment" and fails the suite.

None of these are hard problems. Every one of them is a quiet thirty-minute detour if you find it in a red conformance run at the end, and a two-minute spec edit if you find it at the start.

## Two blockers no specification could have shown

Those eleven were all findable by reading the contract carefully. The next two only turned up because the architecture refused to let me cheat.

The Boundary-Control-Entity adapter enforces one hard rule: a component reaches another only through that component's boundary, and only along an edge declared in the system doc. No reaching into someone else's storage.

### Publishing data you have no way to obtain

`profiles` has to publish a user's `username`, `bio` and `image`. Those live in `users`, which deliberately exposes an account to nobody but its owner, since `get-current-user` is owner-only by design. So `profiles` was specified to publish data it had no declared way to reach. Both specs were internally coherent. Together they were impossible.

The fix was a new `users` boundary operation, `describe-user`, returning a type that has no email and no credential in it. "Never disclose the credential" became structural instead of a matter of discipline.

### A reference that could rot

`articles` stores its author by `UserId` rather than username, because R4.1 lets a username change and a stored reference mustn't rot. But `describe-user` looked up by username. Nothing went from `UserId` to an account, so an article couldn't render its own author.

That produced two more contract changes: `users` answering by identity as well as by name, and `profiles` gaining `view-author` so that no component ever assembles a Profile that `profiles` is supposed to own.

Both blockers surfaced during `apply`, the mode that writes code, and both stopped it dead, because in SDD4J `apply` isn't allowed to write contracts. That constraint felt bureaucratic right up until it caught a design error I'd otherwise have papered over with a convenient shortcut.

## One bug in the gate, and one in a comment

After adding `tags`, the coverage report said "36 ids claimed by tests" when 52 were traced. I was matching requirement ids in one flat global set, but ids are only unique within a capability. `users`, `profiles` and `tags` all declare an `R1.1`.

I proved the hole before fixing it. I renamed `tags` R1.1 to R1.9 in its test, leaving `tags` R1.1 with no test at all, and ran the build:

```
Passed: Total 54, Failed 0
```

Green. Both checks were fooled at once. The gap check saw R1.1 satisfied by *users'* R1.1, and the orphan check saw R1.9 declared by *users'* R1.9. This had been quietly weakening two capabilities for two sessions. Their coverage happened to be genuine, but the gate wasn't the reason for it.

After scoping traces to the owning capability, the same mutation gives:

```
1 requirement(s) have no test carrying their id:
  tags: R1.1
1 test(s) trace an id their capability's spec does not declare:
  tags: R1.9
Failed: Total 54, Failed 2
```

The second bug was a comment. I added a UTC timestamp formatter and justified it in the scaladoc: "`Instant.toString` drops the seconds when they are zero." It doesn't. What it drops is the fractional part, when the nanoseconds are zero. I'd written a plausible-sounding rationale, and it survived three sessions unchallenged because it was a comment, and comments aren't tested.

The rule was still worth keeping, since a rendered shape that changes depending on what time it is makes for a bad rendered shape. It became a system invariant with a test pinning the exact case. But a *why* in a comment isn't evidence. Mine was wrong, and nothing in my process was pointed at it.

That's the same lesson as the gate bug. I only trust `SpecTraceSuite` because I broke it on purpose five times across the project (untraced requirement, orphan id, swallowed token, cross-capability collision, platform timestamp) and confirmed each time that exactly one thing failed, and that it was the right one. An unverified verifier is just a comment that compiles.

## What it cost, what it bought

The overhead is real and I won't pretend otherwise. 118 traced statements is 467 lines of prose to write and keep honest. Test code came out at 2,750 lines against 2,290 lines of implementation, so roughly one and a fifth lines of test for every line of code. Four times, `apply` stopped and handed a decision back to me instead of proceeding, and each of those was a round trip.

Against that, the conformance suite passed on the first run: 13 files, 154 requests, no fixes. That isn't luck. It's the eleven corrections arriving as spec questions instead of failures. Every requirement has a test and every test maps to a requirement, enforced rather than reviewed. The architecture caught two design errors that would otherwise have shown up as awkward code, and both times the fix was a contract change with a recorded rationale. Ten decisions are written down with the alternatives I rejected, so the next person to ask "why isn't this Postgres?" gets an answer instead of an archaeology project.

The honest caveat is that this domain came with an executable external oracle. RealWorld handed me a suite that could tell me I was wrong. Most projects don't have one, and without it a spec-driven loop can converge confidently on the wrong contract. The drift gate still works without an oracle, but the eleven catches above came from having something authoritative to check against.

The next experiment is already set up. Decision D3 put every repository behind a `Ref`-backed in-memory implementation specifically so it can be swapped for Postgres later. A correct swap should touch zero requirement statements and leave all 118 traces intact. If it forces a spec change, that's the interesting result, because it means my specifications were describing my implementation all along.

---

*Code: [github.com/etorres/realworld-ssd](https://github.com/etorres/realworld-ssd). Scala 3.8.4, cats-effect 3.7.1, http4s 0.23.37, cats-mtl 1.7.0, [munit-cats-effect](https://github.com/typelevel/munit-cats-effect) 2.2.0. Built with SDD4J and [Claude Code](https://claude.com/claude-code).*
