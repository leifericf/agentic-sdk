# Zig style: the checkable standard

The checkable Zig standard for this project, grounded in the Zig
Language Reference (Style Guide and Zen), the standard library, and
core-team writing. `check-style` applies this file; `write-zig` writes
to it. This is the normative standard; where a general Zig source
disagrees, follow this file.

Sources of truth that override this file: `zig build` (errors on unused
locals and shadowing, the floor), `zig fmt --check`, and the native
boundary contract in `skills/shared/references/architecture.md`. When a
tool and this file disagree, the tool wins; fix this file.

Run `zig fmt` on changed Zig before it lands. It owns indentation, brace
placement, and trailing commas, so this guide covers what `zig fmt`
cannot: naming, idiom, structure, and judgment.

## How to use this file

Load it once per session. For a single edit, read the rule index, jump
to the section, and ignore the rest. Each section is self-contained.
Each reviewable rule names a token in backticks; a `check-style` finding
cites that token and the section number.

## Rule index

| Section | Topic | One-line rule | Token |
|---|---|---|---|
| 1 | Naming | TitleCase types, camelCase callables, snake_case values and namespaces | `naming` |
| 1 | Naming | No redundant type-name words, no redundant FQN segments, no underscore prefixes | `naming` |
| 2 | Formatting | `zig fmt` is authoritative; 100 columns; minimize nesting | `fmt` |
| 3 | const vs var | `const` everywhere possible | `const` |
| 4 | Types and generics | `comptime T: type` for element types, `anytype` for duck-typed values | `generics` |
| 4 | Comptime | `comptime` and `inline` are deliberate, audited, never reflexive | `comptime` |
| 5 | Functions | Options struct for many params; small; pure leaves | `fn` |
| 6 | Errors | Named `pub const FooError = error{...}`; documented variants; never `anyerror` public | `errors` |
| 7 | Memory | Managed stores the allocator; unmanaged takes it per call; `defer`/`errdefer` on every path | `allocator` |
| 8 | Control flow | Exhaustive `switch` over chained `if/else if`; `@branchHint` on hot and cold paths | `control` |
| 9 | Asserts and builds | Asserts are axioms; never disable; fix wrong asserts; gate expensive asserts to Debug | `assert` |
| 10 | Comments and docs | `//` terse why-only; `///` carries the contract; `//!` optional one line | `comments` |
| 11 | Testing | Co-located; `std.testing.allocator`; fuzz the parsing edge | `testing` |
| 12 | Bytes and numbers | Explicit byte order; widen before untrusted arithmetic; finite at the seam | `numbers` |
| 13 | Change scoping | One change per commit; follow the surrounding file | `scoping` |
| 14 | Project surface | Boundary, handles, realtime; cites `architecture.md` | `boundary`, `realtime` |

## 1 Naming

| What `x` is | Convention | Example |
|---|---|---|
| A type (struct, enum, union, opaque) | `TitleCase` | `Frame`, `ParseResult` |
| A namespace (0-field struct, never instantiated) | `snake_case` | `reader`, `math` |
| A callable that returns a `type` | `TitleCase` | `HashMap(K, V)` |
| Any other callable | `camelCase` | `appendSlice`, `detectPeak` |
| Variables, fields, parameters | `snake_case` | `sample_rate`, `frame_count` |
| Constants and comptime non-types | `snake_case` | `max_fft_size`, `coeff_count` |

- Acronyms and initialisms are ordinary words: `DspChain`, `readU32Be`,
  `XmlParser`, not `DSPChain`, `readU32BE`, `XMLParser`. Even two-letter
  ones follow the rule. (`naming`)
- First method parameter is `self`; bind `@This()` once
  (`const Self = @This();`).
- `SCREAMING_SNAKE` is only for an established external convention (a C
  errno like `ENOENT`, an ABI constant). Everything else, including
  module-level public constants, is `snake_case`. The stdlib uses
  `byte_size_in_bits`, `default_max_load_percentage`, `simple_panic`.
  (`naming`)
- Avoid redundant type-name words: `Value`, `Data`, `Context`,
  `Manager`, `State`, `utils`, `misc`. Everything is a value, all types
  are data, everything is context, all logic manages state; the word
  carries no meaning. (`naming`)
- Avoid redundant fully-qualified namespace segments. A type named
  `JsonValue` inside a `json` file or namespace repeats itself; name it
  `Value`. Files count as namespace segments. (`naming`)
- Refrain from underscore prefixes. Zig has no private fields; name by
  semantics, document invariants in doc comments, and resolve keyword
  collisions with `@"name"`, not `_name`. (`naming`)
- Avoid filler names. `i` is a fine loop index. Units last, descending
  significance: `latency_ms_min`, `confidence`, `coeff_count`.
- A handle type names the native state it wraps, and its struct
  definition lives in the file that creates it; other files see only an
  opaque pointer unless they need the layout. A handle's name matches
  the keyword the host-side wrapper uses for it.

### File naming

| File | Convention | Example |
|---|---|---|
| File struct (top-level type that is instantiated) | `TitleCase.zig` | `Registry.zig` |
| File namespace (pure declarations, no instance) | `snake_case.zig` | `reader.zig`, `math.zig` |
| Directories | `snake_case` | `src/decode/`, `src/net/` |

A file struct that splits into children puts them in a sibling directory
of the same root name, without the `.zig` (`Foo.zig` plus `foo/`).

## 2 Formatting

- `zig fmt` is authoritative: 4-space indent, braces on the same line,
  trailing commas split lists one per line. Touched files pass
  `zig fmt --check`. (`fmt`)
- Line length 100 columns; let a trailing comma wrap long lists.
- `if` gets braces unless it fits on one line (`if (ok) return;`); no
  brace-less multi-line `if`.
- Prefer struct and decl literals (`.{ ... }`) where the result type is
  known; use a named constructor (`init`, a `.empty` sentinel) when the
  type has invariants to establish. (`fmt`)
- Let context coerce: `const len: u32 = @intCast(slice.len);`, not
  `@as(u32, @intCast(...))`.
- Minimize nesting: early-return guards over else-wrapping; success
  path in the `if`; prefer `==` over `!=`. (`fmt`)

## 3 const vs var

Prefer `const` everywhere possible. It states intent, lowers reader
effort, and enables optimization. A `const` pointer still mutates its
pointee. The compiler catches only some needless `var`; apply judgment.
(`const`)

## 4 Types and generics

- `comptime T: type` for element and key types; `anytype` for
  heterogeneous value or pointer parameters the body inspects via
  `@TypeOf`. The split is the stdlib idiom:
  `mem.copyForwards(comptime T: type, ...)`, `mem.span(ptr: anytype)`,
  `fmt.print(comptime fmt, args: anytype)`. (`generics`)
- Type-returning functions are `TitleCase` and return `type`.
- Tagged unions for mutually exclusive state (a state machine: stopped,
  playing, paused); make invalid combinations unrepresentable.
  (`generics`)
- `enum(BackingInt)` for distinct domain ids.
- `?T` for expected absence (a record without optional metadata), `E!T`
  for unexpected failure. Do not model absence with an error union.
  (`generics`)
- Prefer slices over many-item pointers: `[]const f32` for read-only
  input to a numeric kernel, `[]f32` for a caller-preallocated output
  buffer. `*T` or `*const T` is the idiom for one value by reference
  (`addOne` returns `*T`), `*[n]T` for fixed-size, `[*]T` only for
  low-level interior storage. (`generics`)

### Comptime and inline

- `comptime { assert(@sizeOf(Frame) == 48); }` documents and enforces a
  layout invariant at zero runtime cost.
- `@compileError("message")` over `unreachable` for failed comptime
  constraints.
- `comptime` and `inline` are deliberate, not reflexive. Both blow up
  compile times; the team ships a compile-time report for projects that
  overuse them. Reserve `inline` for a measured win, and keep heavy
  `comptime` computation audited, not accumulated. (`comptime`)

## 5 Functions

- Many parameters, or two same-typed adjacent ones, become an options
  struct. No bare `bool` for a behavioral mode; use a named enum. (`fn`)
- Prefer returning values to out-pointers, except for large in-place
  initialization. (`fn`)
- Keep functions short. Approaching a size limit means two
  responsibilities; split by phase or by domain, do not shave lines.
  (`fn`)
- Push `if`s up to the parent and push `for`s down into leaves; leaf
  helpers stay pure. A branch a caller would want to reach belongs at
  the caller, not buried in an effectful loop. (`fn`)
- Visibility: `pub fn` for the public surface, bare `fn` for helpers.
  Private aggregate types are `const X = ...;` without `pub`. (`fn`)

## 6 Errors

- Declare named error sets as public constants and return `FooError!T`.
  Document each variant with a `///` line inside the set. (`errors`)

  ```zig
  pub const ParseError = error{
      /// The input was empty or contained an invalid character.
      InvalidCharacter,
      /// The result cannot fit in the target type.
      Overflow,
  };
  ```

- Reuse `Allocator.Error!T` where that is the real set; an inline
  anonymous set (`error{OutOfMemory}!void`) is for a narrow single-site
  case only.
- Inferred `!T` is fine for private helpers. Never `anyerror` in a
  public API; the stdlib keeps it to test fixtures.
- `try` to propagate; `catch |err|` to handle, always capture, never
  discard. `.?` over `orelse unreachable`; `orelse` only for a real
  fallback. (`errors`)
- Acquire then immediately `defer`/`errdefer` release (section 7).
- Zig errors carry no payload. When a failure surfaces across a
  language boundary, it carries a structured diagnostic the host shell
  renders; the Zig side does not print, it fails, and the shell renders.
  The diagnostic names the failing operation, never a bare error.

## 7 Memory and allocation

Zig has no hidden allocations; every allocation is explicit and paired
with a free. Lifetime rules at the native boundary live in
`skills/shared/references/architecture.md`; honor them.

- Pass `std.mem.Allocator` explicitly, never a global. The allocator is
  part of the signature, not an ambient. (`allocator`)
- Managed versus unmanaged. A data structure that allocates has two
  forms. Managed stores `allocator: Allocator` (`init(gpa) Self`,
  methods do not re-take it). Unmanaged omits it and takes `allocator`
  as the second parameter on every allocating method
  (`append(self, gpa, item)`, `deinit(self, gpa)`). The default for a
  library type is unmanaged; provide managed as a convenience wrapper.
  Prefer the `.empty` sentinel over a deprecated bare `init`. (`allocator`)
- Pair every `alloc`, `create`, or `init` with `defer` (success) or
  `errdefer` (partial construction before ownership transfers). When a
  helper allocates twice, write the errdefer for the first before making
  the second. (`allocator`)
- Scalars copy across the boundary. A host slice is valid only for the
  call. Returned native memory is explicitly owned, copied, or wrapped
  in a handle whose finalizer frees it. The side that creates a handle
  owns its lifetime; never store a handle pointer in a global, never
  retain a host-owned pointer after return. (`boundary`)
- `std.testing.allocator` in every allocating test; a leak fails it.

## 8 Control flow

- Prefer exhaustive `switch` over chained `if/else if` for multi-way
  dispatch on enums, `@typeInfo`, tagged unions, or character classes.
  The compiler enforces exhaustiveness; a chain it could replace is a
  finding. (`control`)
- `if` with a payload capture when the none branch matters
  (`if (opt) |x| ...`); `orelse` for a default; `.?` to assert.
- `defer`/`errdefer` own cleanup ordering (section 7).
- `@branchHint(.cold)` on a panic or rare-error entry point, and
  `@branchHint(.likely)` or `(.unlikely)` on the fast and slow paths of
  a hot loop. The stdlib annotates every panic path and every buffer
  fast-path this way. (`control`)
- `@trap()` terminates an unrecoverable panic.

## 9 Safety, asserts, and build modes

- Assert preconditions, postconditions, and invariants. Split compound
  assertions: `assert(a); assert(b);`. (`assert`)
- `std.debug.assert` is a normal function, not a macro. Its argument is
  always evaluated, even in ReleaseFast. Side-effecting asserts are
  fine; an expensive assert is not elided, so gate it to Debug.
  (`assert`)

  ```zig
  if (builtin.mode == .Debug) {
      const condition = ...;
      assert(condition == .ok);
  }
  ```

- An assert is an axiom given to the compiler, worth more than unit
  tests, more again when fuzzed. Never disable asserts wholesale. A
  wrong assert that passes tests but trips in production must be hunted
  down at once, because later code is built on the false premise.
  Prefer the type system (non-null `*T`, optionals) over an assert that
  restates what the type can say. (`assert`)
- Choose the build mode deliberately. Keep asserts on as runtime checks
  (ReleaseSafe) or as optimization axioms (ReleaseFast); disabling them
  leaves the program running on wrong assumptions. (`assert`)
- Handle every error; most catastrophic failures come from a mishandled
  non-fatal one. Untrusted input never reaches an `unreachable` or an
  unchecked size cast. A corrupt header is expected input, not a panic:
  validate it, return an error, let the shell surface the diagnostic.
  (`assert`)

## 10 Comments and docs

The Zig standard library is the calibration target. Inline `//` comments
are rare; `///` doc comments carry the public contract; `//!` names the
file.

- The density and length budgets apply to inline `//` narration, not to
  `///` contract docs. Core-team `//` is terse (one line, the why only);
  core-team `///` routinely runs several lines because it documents
  effects, complexity, and ownership. (`comments`)
- Inline `//`: comment only what the code cannot say (which allocator
  owns a slice, why a branch is unreachable, an endianness or layout
  invariant, a non-obvious numeric decision). One line; at most three.
  Four is the essay threshold and a finding. Density above one line per
  fifty code lines is a finding. No banners, no commented-out code, no
  change narrative. (`comments`)
- `///` doc comments on public declarations whose contract is not
  obvious from the signature. Document effects ("Invalidates element
  pointers if more memory is needed") and inputs, not mechanism. Omit
  what the name already says. (`comments`)
- Doc-comment vocabulary: use the word **assume** for an invariant whose
  violation is unchecked illegal behavior, and **assert** for one whose
  violation is safety-checked. (`comments`)
- `//!` file-top is optional. When present, a single line naming the
  file's responsibility. Most stdlib files omit it and open with
  imports. (`comments`)
- No `/* */` block comments; Zig has none.
- A well-placed `assert` documents an invariant more strongly than a
  comment; it is enforced in Debug and ReleaseSafe.

## 11 Testing and fuzzing

- Co-locate `test` blocks at the bottom of the file they cover.
  Test-first: write the failing test, watch it fail for the right
  reason, then implement. (`testing`)
- `std.testing` asserts (`expectEqual`, `expectEqualStrings`,
  `expectError`, `expectApproxEqAbs`, `expectApproxEqRel`); do not write
  a manual `if (x != y) return error...` branch in a test. (`testing`)
- `std.testing.allocator` in every allocating test. Cover edges: nil via
  optionals, empty and single-element slices, boundary sizes, overflow
  and negatives on sizes that cross the boundary, unsigned values beyond
  signed ranges. (`testing`)
- For numeric correctness prefer `expectApproxEqRel` with a tolerance
  proportional to the expected magnitude; document the tolerance per
  test. (`testing`)
- Fuzz the parsing or network edge. An assert is worth a thousand unit
  tests, and orders of magnitude more when fuzzed. A parser, decoder,
  or protocol body that takes untrusted bytes gets fuzz coverage; pin a
  fuzzer-found crash as a regression before fixing it. (`testing`)

## 12 Bytes, targets, and numbers

- Multi-byte integers to and from raw bytes use explicit byte order
  (`std.mem.readInt`/`writeInt`), never a host-order reinterpret. A
  big-endian variant of a normally little-endian format is rare but a
  real input. (`numbers`)
- Sizes and counts are `usize`; signed only where negative means
  something. No silent narrowing: `@intCast` only with a justified
  bound. Size and index arithmetic on boundary-crossing values uses
  checked ops (`std.math.add`/`mul`) guarding the access; `+%` and `*%`
  only where wrap is intended and bounded. (`numbers`)
- Widen before arithmetic on untrusted coordinates or lengths. Two
  attacker-controlled `i32` fields can overflow `i32` and panic in a
  safe build before the value is used. Widen both to `i64` before the
  add, then cast the in-range result. A hostile pair degrades to a
  clamped or rejected frame, never a panic. (`numbers`)
- Unsigned integers beyond signed host ranges need an explicit return
  policy before they cross back; the wrapper does not silently truncate.
- Platform branches are comptime-gated on `builtin.target.os.tag` and
  `builtin.cpu.arch`. The target is part of the compile cache key.
- Float `==` is meaningful only for assigned or copied values, never for
  computed results; compare those with a tolerance. Do not expect
  bit-identical floats across platforms or optimization levels.
- Guard the output for finiteness at a numeric seam. Finite input can
  overflow internal math to NaN or infinity. Check with
  `std.math.isFinite` before the result crosses the boundary and degrade
  a non-finite value to the documented absent datum; never let it
  escape. (`numbers`)

## 13 Change scoping

- One logical change per commit; no drive-by renames or style churn in
  untouched code. (`scoping`)
- Follow the surrounding file's conventions; in-file consistency beats
  abstract preference.
- Abstraction earns its place: a helper reduces mistakes or preserves an
  invariant across call sites, not hides a few repeated lines.
  Duplication is cheaper than the wrong abstraction. (`scoping`)

## 14 Project surface: boundary, handles, realtime

These rules are project hardening beyond core-team Zig. They are the
Zig expression of the architecture contract in
`skills/shared/references/architecture.md`; read that file for the full
contract.

- No allocation on a realtime path. A callback, a per-frame renderer, or
  a per-event handler allocates nothing on the steady path. Buffers are
  preallocated at setup; the steady loop reads source buffers and writes
  into preallocated memory. A `try allocator.alloc(...)` in the callback
  is a critical finding, not a style note. (`realtime`)
- No locks on a realtime path. A mutex lock in the callback is a
  critical finding. Use lock-free queues (`std.atomic`); a spin-locked
  pointer exchange is acceptable only when the spin is bounded by one
  cache line. (`realtime`)
- Bounded operation. Every call has a worst-case bound proportional to
  the input size. A hostile length, a header claiming far more data than
  the payload holds, fails fast, validated against the actual length
  before the main loop. (`realtime`)
- Numeric precision. Pick one precision per stage and document the
  conversion at the boundary; avoid mixed-precision arithmetic inside a
  hot loop. (`realtime`)
- Deterministic on a given input. The same input twice produces the same
  output within float precision. A non-determinism finding is a real
  bug; pin it with a regression test against a fixed fixture.

## Grounding

Grounded in the Zig Language Reference (Style Guide and Zen), the Zig
standard library, and core-team writing (Loris Cro, "You Must Fix Your
Asserts"). Naming, formatting, the file-as-struct model, the
assume/assert vocabulary, and the Zen maxims track the Language
Reference. The managed and unmanaged allocator pattern, named error-set
constants, `anytype` dispatch, and `@branchHint` track the standard
library. Assert discipline, `comptime` restraint, and fuzzing at the
edge reflect the core-team posture on correctness.
