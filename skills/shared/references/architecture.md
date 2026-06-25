# Architecture: Functional Core / Imperative Shell

This is the house architecture doctrine. Every `write-<lang>` recipe cites it
instead of restating the shape. It owns the WHAT: how the code is factored.
The HOW work flows across that shape, and the order it lands in, live in
`orchestration.md` (dispatch, fan-out, escalation) and `worktree-model.md`
(topology, integration order, conflict law).

## 1. The split, and why it is load-bearing

Functional Core / Imperative Shell is the primary pattern of this toolkit.
The codebase factors into two layers with a hard contract between them.

**The pure core** is data plus pure functions. It takes values, returns
values. It does no IO, reads no clock, holds no global state, and throws no
exception across its boundary. Its functions are easy to test (call them with
a value, assert on the return), deterministic (same input, same output, every
run), and easy to reason about (no hidden dependency on the world outside the
call).

**The imperative shell** is everything the core refuses to do: IO,
persistence, scheduling, platform calls, native edges, mutation of
long-lived state. The shell adapts inputs to data, calls the core, and
applies the result as effects. It switches on values the core returns and
carries no logic of its own.

The split is load-bearing for three reasons:

- **Testability.** The core is exercised by plain assertions, no mocks, no
  fixtures, no setup ceremony. The shell is thin enough that its behavior is
  checked by a small number of integration tests.
- **Determinism.** The core has no clock, no IO, no global. A test that fails
  fails the same way twice; a bug reproduces.
- **Reasoning.** A reader tracing a core function never hits the world. The
  shell, where the world lives, is narrow and explicit.

## 2. Dependency direction: inward, no cycles

Dependencies point inward. The core depends on nothing. The shell depends on
the core. Native edges depend on both: on the core for the data shapes they
marshal, on the shell for the lifetime that owns them.

**The core never reaches up.** A core namespace, module, or translation unit
never imports, requires, or calls a shell unit, and never names a native
handle type. A violation of this rule is a factoring bug regardless of
whether it compiles. The shell may call the core; the core may not know the
shell exists.

No cycles, anywhere. If two units depend on each other they are one unit, or
the boundary is drawn wrong.

## 3. The house shape: pure core, imperative shell, native wrapper

Three layers, one shape, repeated at every scale:

```
native wrapper    the edges: C ABI, NIF, FFM. Data in, data out.
       |          depends on the shell
imperative shell  IO, state, scheduling, the world
       |          depends on the core
pure core         data and pure functions; depends on nothing
```

A project that has no native edge drops the wrapper layer and keeps the
core/shell split. A project whose entire surface is pure (a library) may keep
only the core. The wrapper layer appears the moment one language calls
another.

The same shape repeats inside a module: a pure namespace, a shell namespace,
and, when the module owns a native call, a thin wrapper. It repeats down to
the function: a pure function and the shell caller that feeds it.

## 4. Per-language expression

Each `write-<lang>` recipe fills in the discipline slot. This section is the
shared statement each recipe cites; it is not a substitute for the recipe.

### C

The pure core is a set of translation units that take POD structs and
pointers to immutable data and return values, allocating through an explicit
owner and never touching a global. Determinism comes from explicit control
flow and from the GC/ownership decision made up front for every allocation.

The imperative shell is the host: the entry points, the loop, the file IO,
the platform calls. It owns the GC roots and the lifetime of host-owned
allocations.

Rigor:

- **GC/ownership decision up front.** Every allocation is GC-owned
  (`gc_alloc_typed`, with temporaries pinned across allocation points) or
  host-owned (malloc, freed on every error path). Decide before writing; do
  not retrofit. When a helper allocates twice, write the pin guard for the
  first before allocating the second.
- **Explicit control flow.** Error classes are chosen consciously:
  recoverable, host, abort-with-a-comment. User input never reaches an abort.
- **Deterministic cleanup.** Every host-owned allocation has a free on every
  path, including the error path.

### Zig

The pure core is the set of subsystems that take data and return data: the
reader, normalizer, validator, planner, the ops. They never thread a file
handle or a writer through. The plan stays a pure value.

The imperative shell is the CLI and the asset layer: file reads and writes,
telemetry emission, diagnostic rendering. Schema validation happens at the
seams.

Rigor:

- **Allocator-per-phase.** Parse arena, validation arena, execution
  allocator, per-node scratch, inspection/output allocator. The allocator is
  passed explicitly as a parameter; no hidden globals.
- **defer and errdefer on every path.** Pair every `alloc`, `create`, or
  `init` with `defer`, or `errdefer` when the value escapes only on the
  success path. When a helper allocates twice, write the errdefer for the
  first before allocating the second. Slices and pointers do not outlive
  their backing memory; an arena that frees at phase end frees everything it
  lent out.
- **No allocation on hot paths.** The audio callback and the per-frame
  renderer allocate nothing on the steady path.
- **Errors as values plus diagnostics.** Error unions drive control flow;
  user-facing failures also carry a structured diagnostic (an EDN-serializable
  map with level, code, message, and, when the value came from parsed EDN, its
  path and span). Cores return or throw diagnostics; they never print.

### Clojure

The pure core is data and pure functions: catalog entities, view-specs,
transactions, processing-op chains, all plain EDN-shaped data; pure functions
transform them. Core namespaces take data and return data, do no IO, never
shell out, hold no clock, thread, or atom.

The imperative shell is persistence, platform, and the application composer:
Datomic wiring, OS integration, lifecycle, scan orchestration. The shell
adapts inputs to data, calls the core, applies the result as effects.

Rigor:

- **Description, not instruction.** Model entities, view-specs,
  transactions, and op-chains as plain data; let pure functions transform
  them. This is the project's architectural identity.
- **Transactions are data.** Build tx-data in a pure function; the shell
  transacts. Never call `d/transact` from a pure namespace; never build
  tx-data in the shell where a test cannot reach it.
- **Bound untrusted seqs before realizing them.** Before `float-array`,
  `byte-array`, or `double-array` turns a caller-supplied seq into a JVM
  array for a native call, `bounded-count` it to the native ceiling plus one
  and reject an over-ceiling seq as data, before realizing. The native length
  guard is the second line of defence, not the first.
- **Decode native scalars to the domain, not merely to finiteness.** A native
  return that carries value-or-sentinel and feeds a schema attribute is
  decoded against the schema's domain (a positive tempo, a member of the key
  enum). A value outside the domain is absence; omit the attribute. Never
  store a sentinel; never fold a wrong value.
- **No per-frame allocation on a Clojure-side hot path.** A shell loop that
  drives the renderer or audio engine per frame allocates nothing on the
  steady path, and the leaks hide in the FFM glue, not the native body.

### Elixir

Authored against the `write-<lang>` skeleton (no grounding recipe yet; the
first real Elixir project deepens this).

The pure core is pure functions in modules: they take data (maps, structs,
tuples) and return data, do no IO, send no messages, hold no state. Pattern
matching and the immutability guarantee do the work the GC does in C and the
allocator does in Zig.

The imperative shell is GenServers and OTP: processes that hold state,
receive messages, talk to the disk and the network. The shell calls the pure
core from inside `handle_call`, `handle_cast`, and `handle_info`, and applies
the return as the new process state plus any side effects.

Rigor:

- **Supervision trees own lifecycle.** The shell is a tree of supervisors and
  workers; restart strategy is explicit; pure functions never start or stop
  processes.
- **Pure core has no process identity.** A core function does not know the
  PID it runs in; it does not send or receive. Testing the core is calling
  it.
- **NIF boundary discipline.** When a NIF calls C or Zig, the native-edge
  contract below applies unchanged. A NIF is the shell of last resort: only
  when the BEAM cannot meet the requirement, never as a default.

## 5. The native boundary contract

When one language calls another, the call crosses the native boundary. Three
concrete edges are in scope:

- **C ABI**, used by Zig (direct), by Elixir NIFs, and by any other language
  that ships a foreign function interface.
- **Elixir NIF**, the BEAM to C ABI edge.
- **the JVM-to-Zig foreign-function edge**, the JVM to Zig edge via the
  foreign-function and memory API.

The contract is the same at every edge:

- **Data in, data out.** The caller marshals domain values into the call; the
  callee returns domain values back. No domain logic on either side of the
  boundary: the pure core decides what to call, the wrapper calls it, the
  core consumes the result.
- **Opaque handles for native state.** A call that returns native state (an
  audio buffer, a window, a codec) returns an opaque handle. Pass it back to
  the side that issued it; never reach into its internals, never decode its
  fields from the other language. The handle's representation is private to
  its owner.
- **Explicit lifetime discipline.** The side that owns the state owns its
  lifetime. A wrapper that borrows a handle states the borrow's duration; the
  owner does not free underneath a live borrow. Arenas, GC roots, and process
  lifetimes are written down next to the handle type, not inferred.
- **Errors cross as values, not exceptions.** A native failure is returned as
  a value (a sentinel-free result map, an error union, an `{:error, _}`
  tuple) the caller can pattern-match. The boundary never throws across the
  edge; an exception that crosses a NIF or an FFM call is a bug.

**Bound untrusted input before realizing it at the native edge.** This is the
load-bearing security rule. A caller-supplied seq, stream, or iterator that
will be materialized into a fixed-size native array is bounded first: counted
against the native ceiling plus one, rejected as data when over the ceiling,
before the realization allocates. The native length guard is the second line
of defence, not the first. If the wrapper realizes first, the host
materializes the whole hostile seq before the native guard fires.

## 6. Description, not instruction

Generalized from prior practice.

Entities, specs, transactions, view-specs, op-chains, plans, and diagnostics
are plain data. Pure functions transform them. The shell applies the result.
This is the project's architectural identity and the rule that keeps the core
testable.

Concretely: build the data in a pure function (or in the core), and let the
shell act on it. Never build tx-data in the shell where a test cannot reach
it; never build a plan where a test cannot reach it; never build a view-spec
where a test cannot reach it. The decision is data; the application of the
decision is the shell's only job.
