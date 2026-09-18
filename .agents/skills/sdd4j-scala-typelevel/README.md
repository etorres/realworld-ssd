# sdd4j-scala-typelevel

The **stack** adapter of the SDD4J family for Scala 3 and Typelevel.

```text
sdd4j + sdd4j-bce + sdd4j-scala-typelevel
```

`sdd4j` owns the workflow, `sdd4j-bce` owns the capability-to-component mapping, and this skill owns
Scala 3 idioms, sbt, munit — plus the overrides that make a Java-shaped method work in a language with
no `package-info.java` and no JUnit.

- `SKILL.md` — the instructions.
- `references/pitfalls.md` — eight failure modes with the evidence that produced them. Read before
  `apply` on an unfamiliar codebase.
- `references/scaffold/` — `RequirementSuite`, `SpecTraceSuite` and `spec-baseline.sh`, parameterised on
  `«base»`. Copy and substitute the base package.
- `references/*-template.scala` — a capability spec and a system doc.

Derived from a complete build: 118 EARS requirements across five business components, verified against an
external conformance suite, then migrated from in-memory storage to PostgreSQL without a requirement
statement changing.
