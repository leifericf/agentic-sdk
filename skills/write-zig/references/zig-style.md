# Zig style: the checkable standard

The checkable Zig standard for this project, grounded in the official
language reference, the standard library, and conventions from major
Zig codebases (TigerBeetle, Bun, Mach). `check-style` applies this
file; `write-zig` writes to it.

Sources of truth that override this file: `zig build` (errors on unused
locals and shadowing, the floor), `zig fmt --check`, and the native
boundary contract in `skills/shared/references/architecture.md`. When
a tool and this file disagree, the tool wins; fix this file.

Run `zig fmt` on changed Zig before it lands; it owns indentation,
brace placement, and trailing commas, so this guide covers what
`zig fmt` cannot.

## 1. Naming

| What `x` is | Convention | Example |
|---|---|---|
| A type (struct, enum, union, opaque) | `TitleCase` | `Frame`, `ParseResult` |
| A callable that returns a `type` | `TitleCase` | `FrameIterator(T)` |
| Any other callable | `camelCase` | `sumF64`, `appendSlice`, `detectPeak` |
| Variables, fields, parameters | `snake_case` | `sample_rate`, `frame_count` |
| True constants and comptime non-types | `SCREAMING_SNAKE` or `snake_case` | `MAX_FFT_SIZE`, `coeff_count` |

- Acronyms are ordinary words: `DspChain`, not `DSPChain`.
- First method parameter is `self`; bind `@This()` once
  (`const Self = @This();`).
- Avoid filler names (`Value`, `Data`, `Context`, `utils`, `misc`). `i`
  is a fine loop index. Units last, descending significance:
  `latency_ms_min`, `confidence`, `coeff_count`.
- A handle type names the native state it wraps, and its struct
  definition lives in the file that creates it; other files see only an
  opaque pointer unless they need the layout. A handle's name matches
  the keyword the host-side wrapper uses for it.

## 2. Formatting

- `zig fmt` is authoritative: 4-space indent, braces on the same line,
  trailing commas split lists one per line. Touched files pass
  `zig fmt --check`.
- Line length 100 columns; let a trailing comma wrap long lists.
- `if` gets braces unless it fits on one line (`if (ok) return;`); no
  brace-less multi-line `if`.
- Prefer struct and decl literals over `Type{}` or `Type.init()`.
- Let context coerce: `const len: u32 = @intCast(slice.len);`, not
  `@as(u32, @intCast(...))`.
- Minimize nesting: early-return guards over else-wrapping; success
  path in the `if`; prefer `==` over `!=`.

## 3. `const` vs `var`

Prefer `const` everywhere possible. It states intent and enables
optimization. A `const` pointer still mutates its pointee. Apply
judgment; the compiler catches only some needless `var`.

## 4. Imports and file structure

- Group imports, blank line between groups, alphabetical within: std,
  third-party (codec libraries, system bindings), local.
- Container declaration order: fields, type aliases, `init`, `deinit`,
  other methods (`pub` before private, related grouped).
- Tests live at the bottom of the file they cover (see section 11).

## 5. Error handling

- Prefer explicit named error sets on public functions; inferred `!T`
  is fine for private helpers. Never `anyerror` in a public API.
- `try` to propagate; `catch |err|` to handle, always capture, never
  discard. `.?` over `orelse unreachable`; `orelse` only for a real
  fallback.
- Acquire then immediately `defer`/`errdefer` release (see section 6).

### Diagnostics at the boundary

Zig errors carry no payload. When a body or wrapper fails to compile,
or a runtime error returns across a language boundary, the failure
surfaces through the structured diagnostic the host shell renders: the
function and signature first, then the source path, then the compiler's
stderr under a stderr key. The Zig side does not print; it fails, and
the shell renders. A diagnostic names the failing operation, never a
bare error.

## 6. Memory and the boundary

Zig has no hidden allocations; every allocation is explicit and paired
with a free. Lifetime rules at the native boundary are conservative
(the contract lives in
`skills/shared/references/architecture.md`); the body must honor them:

- Scalars are copied across the boundary.
- A slice handed in from the host is valid only for the duration of the
  call; a mutable slice may be mutated during the call. The Zig side
  must not retain a host-owned pointer after return.
- Returned native memory must be explicitly owned, copied, or wrapped in
  a handle; the handle's finalizer (registered on the host side) frees
  it. An owned return defines who frees it.
- Pass `std.mem.Allocator` explicitly, never a global. Pair every
  `alloc`/`create`/`init` with `defer` (success) or `errdefer` (partial
  construction before ownership transfers). Write the errdefer for the
  first allocation before making the second.
- Prefer slices (`[]T`) over many-item pointers (`[*]T`): slices carry a
  length and bounds-check.
- `std.testing.allocator` in every allocating test; a leak fails it.

### Handle lifetimes

The side that creates a handle owns its lifetime. A handle created by a
decode call is consumed by the analysis call and freed after; a handle
created at startup lives for the process and has an explicit free on
shutdown; a handle scoped to a view is freed when the view closes. A
body that creates a handle returns it; the matching host-side wrapper
registers a finalizer. Never store a handle pointer in a global; never
reach into a handle struct from another handle's body unless both files
declare the dependency.

## 7. Type system

- Tagged unions for mutually exclusive state (a state machine:
  stopped, playing, paused); make invalid combinations unrepresentable.
- `enum(BackingInt)` for distinct domain ids.
- `?T` for expected absence (a record without optional metadata), `E!T`
  for unexpected failure. Do not model absence with an error union.
- `[]const f32` for read-only slices passed into a numeric kernel;
  `[]f32` for output buffers the caller preallocates.

## 8. Functions

- Many parameters, or two same-typed adjacent ones, become an options
  struct. No bare `bool` for a behavioral mode; use a named enum.
- Prefer returning values to out-pointers, except for large in-place
  initialization.
- Keep functions short. Approaching a size limit means two
  responsibilities; split by phase or by domain, do not shave lines.

## 9. Comptime

- Type-returning functions are `TitleCase` and return `type`.
- `comptime { assert(@sizeOf(Frame) == 48); }` documents and enforces
  layout invariants at zero runtime cost, useful where a struct layout
  must match an external contract.
- `@compileError("message")` over `unreachable` for failed comptime
  constraints.

## 10. Comments

Terse and sparse; the Zig standard library is the benchmark. Clear names
and small functions carry the meaning.

- Comment the why, never the what. Comment only what the code cannot
  say: which allocator owns a slice, why a branch is unreachable, an
  endianness or layout invariant, a non-obvious numeric or rendering
  decision.
- No decorative banners, no commented-out code, no per-line annotation,
  no change narration. A comment block longer than a few lines is itself
  a finding.
- `//!` file-top: one or two lines naming the file's responsibility.
- `///` doc comments on public declarations whose contract is not
  obvious from the signature.
- A well-placed `assert` documents an invariant more strongly than a
  comment; it is enforced in Debug and ReleaseSafe.

## 11. Testing

- Co-locate `test` blocks at the bottom of the file they cover.
  Test-first: write the failing test, watch it fail for the right
  reason, then implement.
- `std.testing` asserts (`expectEqual`, `expectEqualStrings`,
  `expectError`, `expectApproxEqAbs`, `expectApproxEqRel`); do not
  hand-roll `if (x != y) return error...`.
- `std.testing.allocator` in every allocating test. Cover edges: nil via
  optionals, empty and single-element slices, boundary sizes, overflow
  and negatives on sizes that cross the boundary, unsigned values beyond
  signed ranges.
- For numeric correctness, prefer `expectApproxEqRel` with a tolerance
  proportional to the expected magnitude; document the tolerance per
  test.

## 12. Bytes, targets, and numbers

- Multi-byte integers to and from raw bytes use explicit byte order
  (`std.mem.readInt`/`writeInt`), never a host-order reinterpret. A
  big-endian variant of a normally little-endian format is rare but a
  real input.
- Sizes and counts are `usize`; signed only where negative means
  something. No silent narrowing: `@intCast` only with a justified
  bound. Size and index arithmetic on values that cross the boundary
  uses checked ops (`std.math.add`/`mul`) guarding the access; `+%` and
  `*%` only where wrap is intended and bounded.
- **Widen before arithmetic on untrusted coordinates or lengths.** Two
  `i32` fields a malformed payload controls can overflow `i32` and panic
  in a safe build before the value is even used. Widen both operands to
  `i64` before the add, then cast the in-range result. A hostile pair of
  fields must degrade to a clamped or rejected frame, never a panic.
- Unsigned integers beyond signed host ranges need an explicit return
  policy before they cross back; the wrapper does not silently truncate.
- Platform branches are comptime-gated on `builtin.target.os.tag` and
  `builtin.cpu.arch`. The target is part of the compile cache key.

## 13. Safety and assertions

- Assert preconditions, postconditions, and invariants; verify inputs
  before operating. Split compound assertions: `assert(a); assert(b);`.
- Handle every error; most catastrophic failures come from a mishandled
  non-fatal one. Untrusted input must never reach an `unreachable` or an
  unchecked size cast.
- A record with a corrupt header is expected input, not a panic. Validate
  the header, return an error if it is malformed, and let the shell
  surface the diagnostic.

## 14. Realtime and native surface discipline

Bodies that run against a realtime budget (a callback, a per-frame
renderer, a per-event handler) are held to a stricter standard than the
rest of the file:

- **No allocation on a realtime path.** The callback allocates nothing.
  Buffers are preallocated at setup; the callback only reads source
  buffers and writes into preallocated memory. A `try allocator.alloc`
  in the callback is a critical finding, not a style note.
- **No locks on a realtime path.** A mutex lock in the callback is a
  critical finding. Use lock-free queues (`std.atomic`) for cross-thread
  communication; a spin-locked exchange of a pointer is acceptable only
  when the spin is bounded by a single cache line.
- **Bounded operation.** Every call has a worst-case bound proportional
  to the input size. A malformed input (a header claiming far more data
  than the payload holds) must fail fast, not allocate and crash.
  Validate sizes against the actual length before the main loop.
- **Numeric precision.** Pick one precision per stage and document the
  conversion at the boundary; avoid mixed-precision arithmetic inside a
  hot loop.
- **Guard the output for finiteness at a numeric seam.** A finite input
  can overflow internal math to NaN or infinity. Check the result with
  `std.math.isFinite` before it crosses the boundary and degrade a
  non-finite result to the documented absent datum; never let it escape.
- **Deterministic on a given input.** The same input twice produces the
  same output within float precision. A non-determinism finding is a
  real bug; pin it with a regression test against a fixed fixture.

## 15. Change scoping

- One logical change per commit; no drive-by renames or style churn in
  untouched code.
- Follow the surrounding file's conventions; in-file consistency beats
  abstract preference.
- Abstraction earns its place: a helper reduces mistakes or preserves an
  invariant across call sites (a windowing function, a format
  converter), not hides a few repeated lines. Duplication is cheaper
  than the wrong abstraction.

---

*Grounded in the Zig Language Reference style guide, the Bun Zig style
guide, TigerBeetle's TigerStyle, and the Zig standard library.*
