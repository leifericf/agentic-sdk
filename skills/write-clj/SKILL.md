---
name: write-clj
description: Recipe for writing Clojure, Functional Core / Imperative Shell at the function level (namespaces by domain), native wrappers, and the boundary discipline. Invoked when writing Clojure for the project.
user-invocable: false
---

# write-clj

Write Clojure for the project. The standard is
`skills/write-clj/references/clj-style.md` (read it first). The
architecture it implements is Functional Core / Imperative Shell; the
Clojure expression of that discipline, and the native boundary contract,
live in `skills/shared/references/architecture.md`. Namespaces are placed
by domain (recorded in the project's module map, the
`:architecture :modules` entry in the descriptor), and each holds its own
pure and effectful functions. The why behind the constraints is the ADR
log: scan it before designing against an unexplained rule.

The discipline is load-bearing, not a preference. JVM Clojure
semantics are the spec for the surface; check real Clojure behavior for
every edge (nil, empty, laziness, arity, unsigned ranges) before
writing.

1. **Pure functions.** Functions that take data and return data, do no IO,
   never shell out, never transact, hold no clock, thread, or atom. Model
   entities, specs, transactions, and operation chains as plain data; let
   pure functions transform them. This is the project's architectural
   identity: description, not instruction. A pure function may not call an
   effectful one; the dependency direction is into the pure logic, never
   out of it.

2. **Effectful functions.** Functions that own persistence, platform, and
   lifecycle: the database wiring, OS integration, the application
   composer, scan and update orchestration. An effectful function adapts
   inputs to data, calls pure functions, and applies the result as
   effects; it switches on values the pure functions return and carries no
   logic of its own. Mark effectful functions with a trailing `!`
   (`scan!`, `recompile!`, `ensure-library!`).

3. **Namespaces divide by domain.** A namespace is named for the domain it
   owns (a single concept: `cache`, `compiler`, `source`), not for the
   pure/effectful split. A namespace routinely holds both pure functions
   and the `!`-marked effectful functions that drive them; the split is a
   function-level discipline (and the dependency rule of
   `architecture.md` §2), not a namespace boundary. Do not split one
   domain into a `foo` (pure) and a `foo-store`/`foo-shell` namespace;
   that fragments the domain and is a factoring bug.

4. **Native wrappers.** A thin Clojure layer over a foreign-function or
   native edge. Marshal data in, marshal data out, pass opaque handles
   back and forth. No domain logic on either side of the boundary: the
   pure functions decide what to call, the wrapper calls it, the result
   is consumed back in the pure logic.

## Procedure

1. **Place it.** Find the owning namespace by DOMAIN in the module map:
   the namespace named for the concern this code belongs to. Within that
   namespace, keep pure logic pure and put IO, state, and platform calls
   in `!`-marked effectful functions. If you reach for a side effect
   inside a pure function, the design is wrong: move the effect into an
   effectful function (in the same, or the right domain's, namespace) and
   keep the decision pure.
2. **Clojure discipline.** The load-bearing rules:
   - **Description, not instruction.** Model entities, specs,
     transactions, and operation chains as plain data; let pure
     functions transform them. The shell applies the result.
   - **Transactions are data.** Build transaction data in a pure
      function; the shell transacts. Never call the transact API from a
      pure function; never build transaction data in the shell where a
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
    - **A dynamic var's docstring is its value unless it has an explicit
      init.** `(def ^:dynamic *x* "doc")` binds the string as the value,
      not the doc; a nil-guarded optional seam (`(when *x* ...)`) is then
      always truthy and `vreset!` throws on the default. Write
      `(def ^:dynamic *x* "doc" nil)` (three-arg def: docstring then an
      explicit nil init) so an optional seam defaults to nil and the
      guard skips on production paths.
3. **Failure model.** The core takes values and returns values; errors
   inside the core are explicit return values when callers branch on the
   outcome, and `ex-info` with a rich data map at boundaries. The shell
   logs at the edges, never the core. An exception that crosses a native
   edge is a bug; the boundary returns `{:error, _}`-shaped values the
   caller pattern-matches.
4. **Verify like the lanes.** The project's cheap lanes from the
   descriptor (formatter, lint, the unit runner). REPL-driven: evaluate
   small forms, inspect values, adjust. The pure functions are built for
   this: data in, data out, no setup ceremony. For any native-wrapper code,
   run the integration lane that compiles, loads, and calls the native
   side with both a known-good and a malformed input.

## Tests-first

Tests live under the test tree mirroring the namespace layout of `src`.
TDD: a failing test against the intended behavior first, then the
implementation. Test the pure functions directly: data in, data out, no
mocks, no fixtures beyond literal data. An effectful branch a test wants to
reach is a factoring finding: move the decision into a pure function, do not
build a mock. See write-tests for surface selection and the teeth every
assertion needs.

## Boundaries

Owns: the Clojure namespace, its domain (per the module map), and the
data shapes that cross the seams. Cites: the architecture contract in
`skills/shared/references/architecture.md` for the Functional Core /
Imperative Shell discipline (function-level), the description-not-instruction rule, and the
native boundary contract; the module map for placement; the ADR log for
the why. Siblings: write-tests owns the test surface; write-zig owns
the Zig side of a foreign-function edge; write-prose owns the prose
standard. A pure function never reaches up into an effectful one; a wrapper stays
thin.

## Comments and public text

`clojure.core` is the calibration target. The full budget lives in
`references/clj-style.md`; in short: comment only the why, never the
what; a comment block is at most three lines; density above one line
per fifty code lines is a finding; a section marker is a single `;;;;`
line, no banner, no prose underneath. Docstrings carry the
documentation: one to three lines for most fns, longer only when the
arguments have surface area.

Marker idiom: `;;;;` for section labels, `;;;` for namespace-level,
`;;` for top-level forms, `;` for inline. `(comment ...)` is REPL
scratch under a `;;;; Scratch` marker at the foot of the file.

Public-facing text rule: never describe code as hand-written or
hand-rolled in docstrings, docs, or changelog lines, and never carry an
internal process identifier (a phase, task, slice, or run label) into a
comment or commit. See write-prose.
