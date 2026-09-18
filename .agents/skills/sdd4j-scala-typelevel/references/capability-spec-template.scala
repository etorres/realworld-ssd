/** # «Capability»
  * > «One sentence: what this business component owns. Not what it does — what it is answerable for.»
  *
  * ## Boundary
  * - `«operation-id»` — «what a caller gets, in the caller's terms»
  * - `«operation-id»` — «…» _(why: «only when the operation is not obvious from the capability's name —
  *   typically one that exists because another BC may not reach into this one's storage»)_
  *
  * ## Requirements
  *
  * ### R1: «Group name, one boundary operation or one coherent behaviour»
  * - R1.1 — When «trigger», the BC shall «observable outcome».
  * - R1.2 — If «unwanted condition», then the BC shall «rejection, naming what the caller can act on».
  * - R1.3 — While «state», the BC shall «outcome».
  * - R1.4 — Where «feature is absent», the BC shall «default».
  * - R1.5 — «A statement whose reason is not self-evident» _(why: «the reason, so the next session does
  *   not relitigate it»)_
  *
  * ### R2: «Next group»
  * - R2.1 — …
  *
  * ## Entities
  * - «Type owned by this BC»
  *
  * ## Out of scope
  * - «Something a reader would reasonably expect here, and where it lives instead»
  * - «Something deliberately not done, and why — an out-of-scope note becomes visible state once storage
  *   is durable, so write the ones that will leave rows behind»
  */
package «base».«capability»
