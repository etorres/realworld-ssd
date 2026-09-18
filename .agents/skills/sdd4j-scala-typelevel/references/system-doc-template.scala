/** # «System»
  * > «The charter: one sentence on what the whole system serves.»
  *
  * ## Vision
  * - «What the project is for, in a line. Aspiration, not scope.»
  *
  * ## Components
  * - `«a»` calls nothing. It is the root of the dependency graph.
  * - `«b»` may call `«a»` (`«operation»`); never the reverse.
  * - «One line per component. This graph is the only permission to call: a component reaches another
  *   only through its boundary trait, and only along an edge written here.»
  *
  * ## System invariants
  * - S1 — «A rule no single capability owns, in EARS form» _(why: «why it is here rather than restated
  *   in every capability»)_
  *
  * ## Ubiquitous language
  * - «Term» — «definition». Owned by `«component»`.
  *
  * ## Decisions
  * - D1 — «The decision, in the present tense» _(why: «the reason»; rejected: «the alternatives, so they
  *   are not reopened by accident»)_
  *
  * ## Stack
  * - Scala 3 + Typelevel («libraries») on sbt · base package `«base»`
  */
package «base»
