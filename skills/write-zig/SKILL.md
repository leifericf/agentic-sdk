---
name: write-zig
description: Recipe for writing Zig, native bodies and edge wrappers, the allocator and lifetime discipline, and the hot-path rules. Invoked when writing or editing any Zig source.
user-invocable: false
---

# write-zig

Write Zig for the project. The standard is
`skills/write-zig/references/zig-style.md` (read it first). The
architecture it implements is Functional Core / Imperative Shell; the
Zig expression of that split, the allocator-per-phase discipline, and
the native boundary contract live in
`skills/shared/references/architecture.md`. Placement comes from the
project's module map (the `:architecture :modules` entry in the
descriptor). The why behind the constraints is the ADR log: scan it
before designing against an unexplained rule.

Zig in this project means one of three things:

1. **A native body at a language edge.** Real Zig behind a C ABI, a NIF,
   or a foreign-function boundary, never a weakened DSL. The wrapper the
   host language generates provides ergonomic locals; inside the body,
   ordinary Zig is free: comptime, allocators, SIMD, C imports via
   `@cImport`, packed structs. Where the body lives is the threshold
   rule below.
2. **A Zig subsystem.** A pure-core subsystem written in Zig (a reader,
   normalizer, validator, planner, or an ops module) that takes data and
   returns data and never threads a file handle or a writer through. The
   plan stays a pure value.
3. **Test fixtures and C interop headers** when a system library is
   involved. `@cImport` brings the declaration in; never translate a
   system header by hand.

## Where the body lives

Inline bodies are for live exploration and tiny accessors (one
expression or a few lines, no C interop). Before a body lands it moves
to the module's co-located `.zig` when any of these hold: it links a C
library or uses `@cImport`, `@cInclude`, or `@cDefine`; it `@import`s a
resource or uses `@embedFile`; or it exceeds about 25 lines. One file
per module, no sibling `@import` splits that defeat the resource
constraint. Moving a body from inline to file is content-preserving: the
compile cache keys on the normalized Zig text, not the path, so it costs
one recompile.

## Procedure

1. **Decide the boundary, not the internals.** The signature is the
   whole contract: data in, data out, opaque handles for native state
   (see the native boundary contract in
   `skills/shared/references/architecture.md`). The body works within
   the boundary; it never tries to reshape what crosses it. Scalars copy
   across; a slice handed in is valid only for the duration of the call;
   returned native memory is explicitly owned, copied, or wrapped in a
   handle whose finalizer frees it.
2. **Zig discipline.** The load-bearing rules:
   - **Allocator-per-phase.** Parse arena, validation arena, execution
     allocator, per-node scratch, output allocator. Pass
     `std.mem.Allocator` explicitly as a parameter; no hidden globals.
     The allocator is part of the signature, not an ambient.
   - **defer and errdefer on every path.** Pair every `alloc`, `create`,
     or `init` with `defer`, or `errdefer` when the value escapes only
     on the success path. When a helper allocates twice, write the
     errdefer for the first before allocating the second. Slices and
     pointers do not outlive their backing memory; an arena that frees
     at phase end frees everything it lent out.
   - **No allocation on hot paths.** A realtime callback or a per-frame
     renderer allocates nothing on the steady path. Allocate buffers at
     setup; the steady loop only reads source buffers and writes into
     preallocated memory. A `try allocator.alloc(...)` in the steady
     loop is a critical finding, not a style note.
   - **Errors as values plus diagnostics.** Error unions drive control
     flow; user-facing failures also carry a structured diagnostic (a
     serializable record with level, code, message, and, when the value
     came from parsed input, its path and span). The core returns
     diagnostics; it never prints. Prefer explicit named error sets on
     public functions; never `anyerror` in a public API.
   - **Guard the output for finiteness at a numeric seam.** A finite
     input can overflow internal math to NaN or infinity; sanitizing
     only the input is insufficient. Check the result before it crosses
     the boundary and degrade a non-finite value to the documented
     absent datum; never let it escape.
   - **SIMD where it earns its place.** `@Vector` and `@shuffle` for the
      inner loops. Benchmark before adopting; a realtime path cares about
      the 99th percentile, not the average.
3. **Failure model.** A malformed input is expected, not a panic.
   Validate headers and sizes before the main loop; bound the work
   proportional to the input size so a hostile length fails fast instead
   of allocating and crashing. Untrusted input never reaches an
   `unreachable` or an unchecked size cast. Errors cross the native edge
   as values, never as exceptions; an exception that crosses a NIF or a
   foreign-function call is a bug.
4. **Verify like the lanes.** `zig fmt --check` on changed source; the
   build lane (the floor); the module's tests. For any slice, pointer,
   handle, or allocator code, run it through a test that uses
   `std.testing.allocator` so a leak fails the test, and through the
   integration lane with both a known-good and a malformed input.

## Tests-first

Co-locate `test` blocks at the bottom of the file they cover. Write the
failing test, watch it fail for the right reason, then implement. Use
the `std.testing` asserts (`expectEqual`, `expectEqualStrings`,
`expectError`, `expectApproxEqAbs`, `expectApproxEqRel`); do not
hand-roll error branches in tests. `std.testing.allocator` in every
allocating test. Cover the edges: empty and single-element slices,
boundary sizes, overflow and negatives on values that cross the
boundary, unsigned values beyond signed ranges. For numeric correctness
prefer `expectApproxEqRel` with a tolerance proportional to the expected
magnitude, and document the tolerance at the assertion. See write-tests
for surface selection and the teeth every assertion needs.

## Boundaries

Owns: the Zig body, its allocator and lifetime discipline, and the
diagnostics it returns. Cites: the architecture contract in
`skills/shared/references/architecture.md` for the Functional Core /
Imperative Shell split, the allocator-per-phase rule, and the native
boundary contract; the module map for placement; the ADR log for the
why. Siblings: write-tests owns the test surface; write-c owns the C
side of a C ABI edge; write-clj and write-elixir own the host side of a
foreign-function or NIF edge. A wrapper stays thin: marshal values in,
marshal values out, pass handles back and forth. No domain logic on
either side of the boundary; the pure core decides what to call, the
wrapper calls it, the core consumes the result.

## Comments and public text

Terse and sparse, like the standard library. `//!` file-top: one or two
lines naming the file's responsibility. `///` doc comments on public
declarations whose contract is not obvious from the signature. `//` for
inline, and only what the code cannot say: which allocator owns a slice,
why a branch is unreachable, a layout or endianness invariant, a
non-obvious numeric or rendering decision. No banners, no commented-out
code, no change narration. A well-placed `assert` documents an invariant
more strongly than a comment; it is enforced in Debug and ReleaseSafe.

Public-facing text rule: never hand-written or hand-rolled in docs, doc
comments, or commit lines, and never an internal process identifier (a
phase, task, slice, or run label) in a comment or commit. See
write-prose.
