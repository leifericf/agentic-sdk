---
name: write-tests
description: Recipe for writing tests, surface selection across pure core, native edge, shell, and full vertical, property and generative discipline, and the teeth rules. Invoked by writer agents when a test unit is dispatched.
user-invocable: false
---

# write-tests

Write tests for the project. The deep taxonomy (the four engines of
exhaustiveness, the leak lane, the coverage loop, compile-once fixtures
across the boundary, the surface-to-tier map) lives in
`skills/shared/references/pyramid.md`. Read it before writing anything
past a single unit test. This file is the hub: pick the surface, follow
the rules, commit in the order that keeps history green.

**Read first: write-prose.** Test names, context strings, docstrings,
and the fixture's comments are prose. The behavior string is a sentence
a reader parses in one pass. Describe the behavior, never the process:
no phase, task, slice, or run identifier in a test description.

## Pick the surface first

The suite mirrors the source layout. The pure core carries the bulk;
shell and native carry fewer, heavier integration tests. The four
surfaces map onto the tiers in `pyramid.md`:

- **Pure core.** Plain data in, data out. No mocks, no fixtures beyond
  literal values. This is where correctness is decided and where the
  architectural commitments live: description-not-instruction, plain
  data, the standalone module that requires no other. Most of the suite
  is here.
- **Native round-trip.** An integration test that builds the native
  side, loads the result, and asserts the returned value or the handle's
  observable state. Real native code, real external libraries, real
  cache. Never mock the toolchain.
- **Persistence and shell.** An integration test that stands up a
  scratch store under a test-owned temp path, installs the schema (or
  equivalent), writes, and reads back. Round-trip every mutation through
  a real transaction (or store call); never mock the peer API. Assert
  history where the store keeps it: as-of and since queries see
  retractions.
- **Generated source.** A function that emits source another engine
  executes (Datalog, SQL, a wire payload, native source) is asserted as
  a string for syntax, and run through the owning engine for behavior
  (see below).

When in doubt which tier a behavior needs, read the surface map in
`pyramid.md`.

## The tools, language-neutral

The project's descriptor names the concrete tools; the roles are stable
across them.

- **Unit test runner** for unit and integration tests. Group related
  assertions under a context or describe block whose name describes the
  behavior in question, not the function under test.
- **Schema and generator library** for shapes and generated input.
  Shared generators live one per domain, derived from the entity
  schemas.
- **Property library** for generative tests. Drive properties through
  the library's entry point with a bounded trial count for the default
  run; a deeper sweep raises the count behind an alias.
- **Model-based stateful testing** for shell sequences: startup, load,
  external event, reload, shutdown. The model checks the invariants a
  sequence can violate.
- **Coverage tool** for pure-core coverage and the floor.

## Maximize the testable surface; mark the irreducible residual

Some code cannot run in CI: a path that needs a display, a device, or a
window the headless runner cannot provide. Before writing such a path,
split it so the most logic possible is pure and unit-testable, and the
hardware-bound residual is as thin as it can be.

- **Separate pure logic from the device or window calls.** The diff, the
  bounds and coordinate math, the wire serialization, and the recreate
  decision (which trigger fired, given sizes and result codes) are pure
  functions of data: test them headless, exhaustively, with the
  adversarial-bounds and allocation patterns below. Only the calls that
  touch the device are the irreducible residual.
- **Mark the residual explicitly, do not imply coverage.** A display- or
  device-bound test carries an honest marker naming why it cannot run
  here, so a green suite never pretends to cover the runtime. State the
  cheapest real gate in the marker (a manual interactive run, or the
  offscreen path that shares the same code). This is the deliberate
  exception to the rule that bans skipping a behavior a test could
  assert.

## Behavior-first

Each unit of work opens with a scenario that names the behavior in plain
prose: Given a state, When a query or action applies, Then the expected
results follow. Express it as a test whose name and context strings read
as the sentence; the scenario asserts the user-visible outcome, not an
internal step. These are the acceptance layer. They sit above the unit
tests and are committed red on their own (see the choreography below).

## Float tolerance

Numeric output is floating point. Never assert exact equality on a
computed float. Assert within a stated tolerance, and document the
tolerance at the assertion. Pick the tolerance from the quantity, not
from convenience: a tolerance loose enough to hide a regression is worse
than no test. Cross-platform numeric paths may need a looser bound; state
why at the site.

## Spec-first discipline

When the implementation does not exist yet, write the test against the
intended behavior: the spec's contract, or the language's canonical
semantics for a pure surface. Land it, and let it fail. A named API that
does not exist yet is a contract to test against, not a reason to wait.
Mark nothing as skipped. A test that cannot pass reveals a real gap (file
it, fix the source), an upstream platform difference (document it at the
site), or harness debt (fix the harness). Skip-lists and weakened
assertions are never the fix.

Assert behavior, not implementation: the returned value, the error kind,
the printed form, the normalized shape, the query result. Never reach
into internals that factoring may change, and never inspect a handle's
contents: pass it back to the side that issued it. A shell branch that
feels untestable is a factoring finding: move the decision into the pure
core, do not build a mock.

## Every assertion must be able to fail

This is the teeth behind the four engines in `pyramid.md`. An assertion
that cannot fail proves nothing and hides the gap it pretends to cover.

- **No tautological disjunction.** An `or` in an assertion where one arm
  is always true makes the whole assertion always true. Each branch must
  be reachable and must be able to fail on its own. Drop the always-true
  arm.
- **Prefer the tightest oracle.** The exact expected value over a type
  predicate; `:ok` over `is it a keyword`.
- **Confirm each generative arm executes.** A dead arm asserts nothing.
  An oracle that still passes under an injected fault has no teeth.
  Inject the fault once and confirm the arm goes red, for every
  determinism and well-formedness arm, not only the leak oracle.
- **A model-based test needs an independent oracle.** A model that
  mirrors the implementation's own logic, or a check that compares two
  calls of the same pure function, passes by construction and proves
  nothing. Re-derive the expected state a different way (an exact set
  folded by an explicit inverse, a count crossed against an independent
  enumeration), then probe it: inject a subtly wrong value and confirm
  the model goes red, then revert.
- **A passthrough seam is not a test of the seam.** An identity- or
  no-op-backed fallback for an unimplemented seam re-checks in-memory
  data and lets the round-trip claim pass vacuously. Gate the arm on the
  real seam resolving, or it asserts nothing.
- **Match the prose to the comparison.** If a context string or a
  comment claims bitwise or exact, the assertion is bit-exact on the raw
  bits, or the prose is softened to match a value equality or a
  tolerance.

## A compiler's output must run, not just match a shape

When a pure function emits a form another engine executes (Datalog, SQL,
a wire payload, native source), a string or shape assertion proves the
syntax it emitted, never that the engine accepts it. File the run-it-live
test as the compiler's owning test in the module that holds the engine
(run a compiled query against a seeded scratch store, a generated native
body against a real build and load), in the same commit family as the
compiler. Cover each branch the compiler can emit, every connective and
every operator-by-field shape, not one happy path. A shape-only test with
no paired live run is an unowned deferral.

## A multi-pass derivation's passes must agree

A derivation computed by several passes that each walk the same
structure (a container laid out, flattened, and hit-tested; a document
rendered, measured, and picked) drifts when the passes derive a value
independently. Add a property: every pass's output agrees across the
empty, single, and multi-element states, and under a clamped or
degenerate layout. A pass that seats an element another never draws, or
a pickable region over empty space, fails it.

## A layout that places text is correct only against the real bake

A layout that places text is correct only against the metrics the
renderer actually paints, not design line-heights. Every text-bearing
placement gets a headless geometry test under the layout's real-bake
constants: assert no vertical overlap and no horizontal overrun, across
the empty, single, and long-string states. The offscreen render lane
(real font) is the paired live proof; a manual check on the live run is
the irreducible residual. A substitute font in a gallery measures
differently and hides the bug.

## Self-cleaning fixtures

Scratch state (a temp store, compiled native libraries, the artifact
cache) lives under a test-owned temp path, recreated at the top of each
test. Tests pass in any order and on rerun. Fixtures are small and
committed under the fixtures tree: a minimal valid input, an empty
input, a truncated header, the single and multi variants, the integer
and float encodings where both are accepted. Synthesize larger or odder
inputs in the test itself. Native fixtures compile once per signature
shape; see the compile-once rule in `pyramid.md`.

## Adversarial bounds on untrusted input

When a body does arithmetic on fields a payload controls (a coordinate,
a width, a declared length, an index), the happy-path test never finds
the overflow. Fuzz the bounds: generate values across the full field
range, not just plausible ones. Include the boundary and beyond: 0, 1,
-1, the type's min and max, and values near them where two added fields
cross the type's limit. Drive the real call with each and assert it
degrades as data (a clamped or rejected frame, an error keyword), never
a panic or a crash. Drive it through the property library over the
adversarial range and pin any shrunk crash as a regression beside the
property.

The same gap exists on the pure path: a derivation that never validates
its input, so a value built directly (bypassing the schema, or pulled
from a cache) arrives unvalidated. Drive the pure function with a
directly-built value carrying an over-cap collection, a wrong type where
one is assumed, and an oversized string; assert the output stays bounded
and nothing throws. The schema-valid path is not the test; the
unvalidated path is.

## Bound untrusted input before realizing it at the native edge

A wrapper that turns a caller-supplied seq into a primitive array must
reject an over-ceiling input without realizing it (see the architecture
contract). The detection needs a lazy seq, not a counted vector: feed
each wrapper an over-ceiling lazy seq (or an infinite one) and assert it
is rejected as data before realization, so the native guard is never the
only thing that fires.

## Out-of-domain scalar decodes to absence

A decode that turns a native value-or-sentinel into a domain attribute
(see the architecture contract) is tested with the sentinel and
degenerate values, not just a healthy one. Feed it the zero, the
negative, the unknown enum, and assert the attribute is omitted from the
result, never emitted as an invalid value and never folded to a wrong
enum member. The oracle is domain membership, not finiteness: validate
the produced record against the schema and assert the absent attribute
is genuinely absent.

## Non-finite escape from a numeric seam

A finite input can overflow internal math to NaN or infinity, and the
happy-path fixture never triggers it. Every numeric seam taking
untrusted input carries a large-finite-input arm: drive it with samples
at a near-overflow magnitude and assert no NaN or infinity crosses the
boundary. The result is the documented absent datum, never a non-finite
float. This is the numeric counterpart of the adversarial-bounds fuzz;
sanitizing only the input is not enough, so the assertion is on the
output.

## Per-frame allocation on a hot path

A loop that drives a renderer or a realtime callback per frame must
allocate nothing on the steady path. The native-side allocation probe
covers the native body; it does not see host-side allocation. That gap
needs its own zero-allocation assertion.

Assert it without a profiler: drive the steady loop body N times after a
warmup, sample allocated bytes around the run, and assert the per-frame
delta is at the noise floor (a small fixed bound, not exactly zero,
since a managed runtime allocates outside your control). Use the
language's per-thread allocation counter, or assert against a counting
allocator where one is injectable. Drive the real call site (the held
handle path), not a convenience wrapper, or the test passes while the
loop still leaks. Pin the steady frame: same input, same size, so any
per-frame delta is the allocation under test.

## A live-driver seam: real units and every branch

A pure decision fed by a live capture site is only as correct as the
contract at the seam, and a unit test in fabricated self-consistent
values proves neither half of it. Two arms close the gap:

- **Drive the pure function at the producer's real scale, and pin the
  unit.** A decision comparing a gap to a threshold is driven with a
  value at the scale the live site actually stamps, and the producer
  gets a regression pinning its unit. A test that fabricates both sides
  in the same arbitrary unit passes while the live seam is broken.
- **Drive the degenerate loop branch, not just the steady one.** A
  steady-loop invariant (drain, clear, no-alloc) gets a test over the
  degenerate branch: minimized, paused, or error. The invariant must
  hold on every branch; an accumulator that grows unbounded on a paused
  branch passes every steady-path test.

## The leak lane

Owned-resource returns (native memory, file descriptors, handle pools)
promise the resource is released. One acquisition counter tracks the
balance; after any sequence of create, use, and release, the counter
returns to zero. This is asserted after every test sequence that touches
an owned resource, not as a separate suite. A non-zero counter is a
leak, not a tolerance question: the lane uses exact integer equality
with zero. See `pyramid.md` for the full statement.

## The TDD commit choreography

Strict red-green, reconciled with bisect. The rule that governs every
commit: bisection must always land on a working state. That splits the
two kinds of test across two commit shapes:

1. **The behavior scenario is the spec. Commit it red, on its own.** It
   names the behavior before any code exists and sits red until the
   units beneath it land. This is the one deliberate red commit. It is
   the specification, not a broken build, and the log reads that way.
2. **A fine-grained unit test ships in the same commit as the code that
   turns it green.** Never commit a failing unit test alone. Write the
   test, write the implementation that flips it, commit both together.
   Every such commit builds and passes its owning tests, so bisect
   never lands on a half-finished unit.
3. **Refactor with the tests green, in its own commit.**

So the history reads: one red scenario, then a run of green commits each
carrying a unit test plus the code that satisfies it, until the scenario
flips green. A failing property has found a real bug; fix it in a
dedicated change and pin the shrunk minimal case as a regression beside
the property.

Commit messages follow write-commit: single line, category first. New
suites, generators, fixtures, and harnesses take `Tests:`. The coverage
floor takes `Build:`.

## Boundaries

Owns: surface selection, the teeth rules, the float-tolerance and
spec-first discipline, and the red-green commit choreography. Cites:
`skills/shared/references/pyramid.md` for the deep taxonomy, and
`skills/shared/references/architecture.md` for the three-way split and
the native boundary contract the boundary tiers assert against.
Siblings: write-<lang> owns the implementation a test pins; write-commit
owns the commit form the choreography lands; check-style owns whether
the test prose reads clean. Does not write implementation; a failing
test it cannot turn green is a forward task for the owning writer, not a
mock.

## References

- `skills/shared/references/pyramid.md`: the four engines, the leak
  lane, the coverage loop, compile-once fixtures, and the
  surface-to-tier map.
- `skills/shared/references/architecture.md`: the three-way split and
  the native boundary contract the boundary tiers assert against.
- write-commit: the red-green commit choreography this serves.
