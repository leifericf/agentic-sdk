---
name: write-clj
description: Recipe for writing Clojure, the pure core and imperative shell split, native wrappers, and the boundary discipline. Invoked when writing Clojure for the project.
user-invocable: false
---

# write-clj

Write Clojure for the project. The standard is
`skills/write-clj/references/clj-style.md` (read it first). The
architecture it implements is Functional Core / Imperative Shell; the
Clojure expression of that split, and the native boundary contract,
live in `skills/shared/references/architecture.md`. Placement follows
the three-way split (pure core, shell, native wrappers) recorded in the
project's module map (the `:architecture :modules` entry in the
descriptor). The why behind the constraints is the ADR log: scan it
before designing against an unexplained rule.

The three-way split is load-bearing, not a preference. JVM Clojure
semantics are the spec for the surface; check real Clojure behavior for
every edge (nil, empty, laziness, arity, unsigned ranges) before
writing.

1. **Pure core.** Namespaces that take data and return data, do no IO,
   never shell out, never transact, hold no clock, thread, or atom.
   Model entities, specs, transactions, and operation chains as plain
   data; let pure functions transform them. This is the project's
   architectural identity: description, not instruction. The standalone
   library module (if the project ships one) lives here and depends on
   nothing; the dependency direction is into the core, never out of it.

2. **Imperative shell.** Namespaces that own persistence, platform, and
   lifecycle: the database wiring, OS integration, the application
   composer, scan and update orchestration. The shell adapts inputs to
   data, calls the core, applies the result as effects. It switches on
   values the core returns and carries no logic of its own.

3. **Native wrappers.** A thin Clojure layer over a foreign-function or
   native edge. Marshal data in, marshal data out, pass opaque handles
   back and forth. No domain logic on either side of the boundary: the
   pure core decides what to call, the wrapper calls it, the core
   consumes the result.

## Procedure

1. **Place it.** Find the owning namespace from the three-way split in
   the module map. Pure logic goes in the core; IO, state, and platform
   calls go in the shell; the host side of a native call goes in a
   wrapper. If you reach for a side effect in a core namespace, the
   design is wrong: move the effect to the shell and keep the decision
   in the core.
2. **Clojure discipline.** The load-bearing rules:
   - **Description, not instruction.** Model entities, specs,
     transactions, and operation chains as plain data; let pure
     functions transform them. The shell applies the result.
   - **Transactions are data.** Build transaction data in a pure
     function; the shell transacts. Never call the transact API from a
     pure namespace; never build transaction data in the shell where a
     test cannot reach it.
   - **Bound an untrusted seq before realizing it into a primitive
     array.** Before `float-array`, `byte-array`, or `double-array`
     turns a caller-supplied seq into a JVM array for a native call,
     bound it to the native ceiling plus one and reject an over-ceiling
     seq as data, before realizing. The native length guard is the
     second line of defence, not the first: if the wrapper realizes
     first, the JVM materializes the whole hostile seq before the native
     guard ever fires.
   - **Decode a native scalar to the schema's domain, not merely to
     finiteness.** A native return that carries value-or-sentinel and
     feeds a schema attribute is decoded against the schema's domain. A
     value outside the domain is absence; omit the attribute. Never
     store a sentinel, never silently coerce an unknown value to a
     plausible one.
   - **A quantity that crosses the seam carries its unit in its name,
     and the producer stamps the unit the pure consumer assumes.** A
     pure decision comparing a duration, a coordinate, or a size against
     a threshold is dead or wrong when the live capture site stamps a
     different unit. Name the unit on the var and the fn
     (`press-time-ms`, not `press-time`); the producer and the pure
     consumer must agree on it.
3. **Failure model.** The core takes values and returns values; errors
   inside the core are explicit return values when callers branch on the
   outcome, and `ex-info` with a rich data map at boundaries. The shell
   logs at the edges, never the core. An exception that crosses a native
   edge is a bug; the boundary returns `{:error, _}`-shaped values the
   caller pattern-matches.
4. **Verify like the lanes.** The project's cheap lanes from the
   descriptor (formatter, lint, the unit runner). REPL-driven: evaluate
   small forms, inspect values, adjust. The pure core is built for this:
   data in, data out, no setup ceremony. For any native-wrapper code,
   run the integration lane that compiles, loads, and calls the native
   side with both a known-good and a malformed input.

## Tests-first

Tests live under the test tree mirroring the namespace layout of `src`.
TDD: a failing test against the intended behavior first, then the
implementation. Test the pure core directly: data in, data out, no
mocks, no fixtures beyond literal data. A shell branch a test wants to
reach is a factoring finding: move the decision into the core, do not
build a mock. See write-tests for surface selection and the teeth every
assertion needs.

## Boundaries

Owns: the Clojure namespace, its place in the three-way split, and the
data shapes that cross the seams. Cites: the architecture contract in
`skills/shared/references/architecture.md` for the Functional Core /
Imperative Shell split, the description-not-instruction rule, and the
native boundary contract; the module map for placement; the ADR log for
the why. Siblings: write-tests owns the test surface; write-zig owns
the Zig side of a foreign-function edge; write-prose owns the prose
standard. The core never reaches up into the shell; a wrapper stays
thin.

## Comments and public text

`;;;` for namespace-level, `;;` for top-level forms, `;` for inline.
Terse and sparse; comment the why, never the what. Comment only what the
code cannot say: an ownership or lifetime constraint at a native
boundary, why a branch is unreachable, a non-obvious algorithmic
decision. No decorative banners, no commented-out code, no change
narrative. A comment block longer than a few lines, or comments
outweighing the code they sit in, is itself a finding.

Public-facing text rule: never describe code as hand-written or
hand-rolled in docstrings, docs, or changelog lines, and never carry an
internal process identifier (a phase, task, slice, or run label) into a
comment or commit. See write-prose.
