# The test pyramid: the deep taxonomy

The full taxonomy behind `write-tests`. The pyramid, most tests to
fewest: unit, property or generative, bounded-exhaustive, edge-value,
negative-space, boundary fuzz, integration and lifecycle,
end-to-end. A fat pure core carries the weight; a thin shell gets
integration and lifecycle coverage; the native edge gets fuzz; a small
end-to-end set covers full verticals.

## The four engines of exhaustiveness

Where an input space is enumerable, enumerate it as data and drive
every case through the pipeline. Each case is a real input a user or
caller could produce. The four engines below are how a surface is
proven, not sampled.

### 1. Bounded-exhaustive structural matrix

The cross-product of enumerable shapes, enumerated and driven through
the pipeline. Real matrices, by surface:

- All supported input formats crossed with the decode or parse path,
  then the parse-then-transform path.
- All entity or widget types in the vocabulary crossed with the
  engine that consumes them.
- All query or DSL operators crossed with field or argument types.
- All boundary-contract types at the native edge crossed with
  argument position, constness, and ownership, asserted against the
  declared signatures.
- All operation kinds crossed with parameter ranges.

### 2. Edge-value vectors crossed with structure

Each type carries its boundary values, crossed with the structural
axis:

- Collections: empty, single-element, at the size limit,
  duplicate-by-value groups of one and many.
- Buffers and streams: empty, shorter than the window or chunk size,
  very long, single-channel and multi-channel where channels apply,
  integer and float encodings where both are accepted.
- Numbers: zero, the type's min and max, the boundaries internal to
  the algorithm (a window or block boundary, a page boundary), NaN and
  infinity where floats flow, a missing or optional field.
- Geometry or layout: a point exactly on a boundary, the
  empty-versus-single-element layout cases.
- Text: unicode in tags, paths, and filenames; very long strings;
  normalization edge cases.

### 3. Negative-space enumeration

The invalid permutations, enumerated, each with its specific error
code as the oracle. The assertion is the typed error code or sentinel,
not a message string (messages are prose and change). The negative
space:

- Malformed bytes, truncated headers, a header claiming more data than
  the payload holds, unsupported or obsolete formats.
- Corrupted or missing required metadata, invalid keys or tags, an
  entity failing its schema.
- Malformed queries, unknown operators, broken range or slice
  definitions (start past end, a range outside the bounds).
- A tampered or expired credential, a malformed config file, a schema
  or migration from an incompatible prior release.

This is the rejection arm of every constructor, validator, and
parser. The coverage loop names the missing case when an arm is
unreached.

### 4. Model-based stateful sequences

Generated command sequences run through a model of the state machine
that no single-shot test reaches, checking that the real system
matches the model after every command. Real models, by lifecycle:

- App or service lifecycle: startup, scan or load, external event,
  rescan or reload, shutdown.
- Mutation workflows: add, batch add, undo, redo.
- Import or ingest pipelines: decompress, flatten, validate, file.
- Licensing or auth: activate, run, deactivate, run.

Each model checks the invariants a sequence can violate: an external
event after a reload reconciles; an undo after a batch mutation
restores the prior set; a failed activation keeps the last good state.

## Every assertion must be able to fail

This is the teeth behind the four engines. An assertion that cannot
fail proves nothing and hides the gap it pretends to cover. Three
failure modes to reject on sight:

- **No tautological disjunctions.** An `or` in an assertion where one
  arm is always true makes the whole assertion always true.
  `assert result == expected or result is not None` cannot fail once
  `result` is non-None. Each branch must be reachable and must be able
  to fail on its own.
- **No dead property arms.** A generative test whose generator cannot
  produce the failing case is theatre. If the generator shrinks to a
  trivial input every time, or classifies every input as passing, the
  property asserts nothing. The generator must cover the input space,
  including its boundary and negative regions, or the property is a
  passthrough.
- **No passthrough seams.** A test whose arrange step sets up the
  exact state the assertion already holds has tested its own fixture,
  not the system. An integration test that mocks the boundary it meant
  to exercise, a unit test that passes the output back as the input:
  both are passthroughs. If deleting the body under test leaves the
  assertion green, the assertion is decorative.

The leak lane below is the canonical example of an assertion with
teeth: exact equality with zero, no tolerance, no `>=`. A non-zero
balance is a leak, full stop.

## The leak lane

Owned-resource returns (native memory, file descriptors, connection
or handle pools) promise the resource is released. One allocation or
acquisition counter tracks the balance: acquisition increments it,
release decrements it. After any sequence of create, use, and
release, the counter returns to zero. This is asserted after every
test sequence that touches an owned resource, not as a separate
suite.

The resources whose balance the lane proves, by surface:

- A buffer or frame created by decode, freed after analysis and peak
  extraction.
- A result struct created by analyze, consumed by the accessors,
  freed after.
- A long-lived engine created at startup, freed at shutdown.
- A session-scoped resource created on open, freed on close.

A non-zero counter after a sequence is a leak, not a tolerance
question. The leak lane uses exact integer equality with zero. A test
using the system's leak-checking allocator (or equivalent) that
reports a leak fails the test.

## The coverage loop

A coverage tool measures how much of the pure core the tests reach
and floors it, so a gap fails the build. The floor rises change by
change. A gap in the pure core is a missing permutation, and it names
the next case to add to one of the four engines, usually a
negative-space arm or an unreached structural cell.

The shell carries a named allowlist. Its IO, platform, storage, and
external-binding code is covered by the integration, boundary-fuzz,
lifecycle, and end-to-end suites, not measured by line coverage. A
shell branch that resists testing is a factoring finding (see
`architecture.md`): the decision moves into the pure core, where
coverage can reach it, rather than behind a mock.

## Compile-once fixtures across the boundary

The native and boundary tiers run a real compile of the native side
where one is involved (a C or Zig compile, a NIF or FFM build).
Identical signature or shape compiles once: the content-addressed
artifact cache keys on the shape, so the bounded-exhaustive boundary
matrix reuses one compile per distinct shape and the suite stays
affordable. End-to-end tests reuse the same cache. Never clear the
cache between cases that share a shape; clear only the test-owned
scratch state under the test temp directory.

## The fixture set

A small, representative fixture set (about 50 entries): every
supported format or shape, the varied cases the verticals exercise.
Reused by every end-to-end test. It exists for repeatability: an
end-to-end test asserts the same outcome on every run because the
fixtures are fixed. Small boundary fixtures (the empty, truncated,
single, multi-channel, encoding variants) live separately for the
native round-trip tier; the fixture set is for full-vertical
scenarios.

## Which tiers apply to which surface

| Surface | Tiers it carries |
|---|---|
| Pure core | Unit (the bulk), property or generative, bounded-exhaustive, edge-value, negative-space. Floored by line coverage. |
| Native edge (decode, analyze, render, watch, hash, any C ABI, NIF, or FFM boundary) | Native round-trip, boundary fuzz, the leak lane. Real native code, real external libraries, real cache. |
| Shell (persistence, platform, app, scripting) | Integration and lifecycle, model-based stateful sequences, history assertions where a history-bearing store applies. Allowlisted for coverage. |
| Full vertical | End-to-end over the fixture set. Fewest tests; the slowest; the behavior unit and property tests cannot reach. |

The boundary fuzz tier deserves its own note: the language-to-native
edge is where malformed input crashes or corrupts state. It gets
dedicated fuzz, random and adversarial inputs to the native functions
(malformed bytes, huge buffers, edge-case results, concurrent calls),
with no mocks. The most subtle bugs live here, so it sits just below
integration in the pyramid, above negative-space enumeration.

## Per-change mapping

A change operationalizes these tiers for the surfaces it touches. A
pure-core-only change is unit, generative round-trip, and
negative-space only: no native, no shell. A change touching the native
edge adds the native round-trip, the leak lane for the resources it
creates, the model-based lifecycle model where a lifecycle is
involved, and the first end-to-end. Each later change extends the
tiers it touches; not every change touches every tier. Read the
change's planned test layers for the exact set.
