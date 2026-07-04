# Elixir style: the checkable standard

Applies to all Elixir in this project: the pure core (plain modules of
functions), the imperative shell (GenServer, Agent, Task, Supervisor,
Application), the NIF wrappers, and tests. `check-style` applies this
file; `write-elixir` writes to it. This is the normative coding
standard; where a general Elixir source disagrees, follow this file.

## What the standard optimizes for

- **Pure core, process shell.** Deciding is a pure function in a module;
  doing is a process that holds state and talks to the world. The split
  is load-bearing, not a preference. The contract lives in
  `skills/shared/references/architecture.md`.
- **Data, not messages, between the core and the shell.** A core
  function takes maps, structs, and tuples and returns the same. The
  shell calls it and applies the return as state plus effects. No domain
  logic lives in a message or a callback.
- **Let it crash, at the edge only.** The supervision tree is the
  recovery strategy for runtime failure. The pure core never crashes on
  expected input; it returns a tagged tuple. Raising is for programmer
  error the caller controls.
- **Boundaries are explicit.** A NIF is the shell of last resort, and
  the native boundary contract applies to it unchanged.

## Core vocabulary

- **Pure core:** a module of functions with no process identity. It does
  not read or write the process dictionary, send or receive messages,
  touch the clock, or perform IO. Testing it is calling it.
- **Imperative shell:** GenServer, Agent, Task, and the supervisors that
  own them. The shell holds state, receives messages, and applies the
  core's result.
- **Supervision tree:** the structure that owns worker lifecycle and
  restart strategy. Pure functions never start or stop processes.
- **Tagged tuple:** the `{:ok, value}` / `{:error, reason}` shape the
  core returns for expected outcomes the caller branches on.

## The spec is Elixir and the BEAM

- Canonical Elixir (current minor) on the BEAM defines correct behavior.
  Follow the community Elixir style guide where a norm exists; do not
  invent house style where one already does.
- A public surface is ordinary Elixir: functions return values,
  recompiling a module reloads it, and the public API is what `iex` and
  tests call. Match that expectation; surprising semantics at the public
  surface are findings.
- Mix is the build tool; `mix format` is authoritative for layout.
  Credo is the lint floor; Dialyzer (or a gradual typer) checks typespec
  claims where the project adopts one.

## Functional core, imperative shell, native wrappers

The spine separates deciding from doing, with a native edge for what
must run on the metal.

- **Pure core.** Modules of functions that take data and return data, do
  no IO, send no messages, hold no state. A standalone library module is
  the canonical case: it depends on nothing but the data shapes it
  consumes.
- **Shell.** GenServers and other OTP behaviours that hold state,
  receive messages, and talk to the disk and the network. The shell
  calls the core from a callback and applies the return as the new state
  plus any side effects.
- **Native wrappers.** A thin module over a NIF that calls C or Zig.
  Marshal data in, marshal data out, pass opaque handles back and forth.
  No domain logic on either side of the boundary.

Most tests target the core directly: data in, data out. Shell tests
start a supervised process tree, exercise it, and let ExUnit tear it
down. A shell branch a test wants to reach is a decision that belongs
in the core: move it, do not mock the process.

## Naming

- Modules are `PascalCase` aliases under the project's top-level
  (`MyApp.Catalog`, `MyApp.Renderer`). One module per file, matching the
  path.
- Functions and variables are `snake_case`. Atoms are `:snake_case` or
  `:snake-case` as the project convention records; keep one style.
- Predicates end in `?` (`valid?`, `ready?`); functions that may raise
  end in `!` (`parse!` raises, `parse` returns a tagged tuple).
- Booleans are `true` / `false` atoms; do not invent truthiness.
- Keep names domain-meaningful. `value`, `data`, `context`, `utils` are
  filler. Units last, descending significance: `latency_ms_min`,
  `confidence`.

## Module structure

- Lead each module with `defmodule`, then `@moduledoc` (one line for a
  small module, longer for the public API), then `@moduledoc false` for
  internal modules.
- Public functions above private (`defp`). Group related functions.
- `@doc` on every public function: what it returns and the shape of the
  inputs that require it, not the mechanism.
- `@spec` on public functions whose contract is not obvious from the
  name and the pattern. Keep specs honest; a Dialyzer (or typer) warning
  the project enforces is a finding.
- Use module attributes for compile-time constants and configuration,
  not as hidden mutable state at runtime.

## Comments and docstrings

The Elixir standard library is the calibration target: inline comments
are rare one-liners, and documentation lives in `@moduledoc` and `@doc`.

- Comment the why, never the what. Comment only what the code cannot
  say: a restart-strategy choice, a NIF scheduling decision, another
  constraint a future reader could not derive.
- A comment block is at most three lines; one line is preferred. Four
  lines is the essay threshold and a finding. File inline comment
  density above roughly one line per fifty code lines is a finding.
- Delete or move. Delete a comment that restates the next line,
  describes mechanism, or narrates history. Move a design rationale to
  an ADR and cite it by path.
- No banners. No `---` rules, no ASCII art, no decorated separators.
- No commented-out code; the VCS holds history.
- `@doc` is the documentation. One to three lines for most functions:
  what the function returns and the shape of inputs that require it,
  not the mechanism. A long `@doc` is earned by argument surface area
  (an options map, a DSL, a public primitive), never by a clever
  implementation.

## Pattern matching and control flow

- Pattern match in the function head; prefer multiple clauses over a
  single clause with a `cond`. Make invalid states unrepresentable by
  which clause matches.
- `with` for happy-path chaining across `{:ok, _}` / `{:error, _}`
  returns; the `else` clause names every shape the chain can reject. Do
  not swallow an unmatched branch.
- Guard clauses for simple predicates in the head (`when is_integer(n)`),
  not for logic that belongs in the body.
- The pipe `|>` chains pure transformations left to right; the leftmost
  expression is the data flowing through. Keep pipes to pure steps; a
  side-effecting step mid-pipe is a factoring smell.
- `case` for branching on a tagged tuple; `cond` for disjoint tests;
  function clauses for branching on shape.

## Data and domain modeling

- Default to maps and structs with atom keys for entities and options.
  Reach for a struct when a fixed shape with named fields aids reading
  or pattern matching; reach for a plain map when the shape varies.
- Model entities as data with a `:__struct__` or a discriminator; do not
  hide behavior behind them. The domain stays independent of storage and
  rendering.
- Use keyword lists only for ergonomic options at a call site; use maps
  and tuples for data that flows through the core.
- Ecto schemas (or the project's data layer) describe storage shape;
  keep them out of the pure core. The core takes and returns plain
  domain data; the shell translates at the seam.

## State, processes, and concurrency

- GenServer for a named process that holds state and handles
  synchronous or asynchronous calls. Agent for simple wrapped state;
  Task for a one-off asynchronous computation; Registry for name lookups.
- Supervisors own workers; choose restart strategy for what a crash
  invalidates. The application's top supervisor starts the tree at boot.
- The pure core does not spawn, send, or receive. If a decision needs to
  schedule work, it returns a descriptor the shell acts on.
- Bound cross-process talk to tagged tuples and documented messages. A
  process that receives an undocumented shape is a finding.

## Native and NIF discipline

The native boundary contract lives in
`skills/shared/references/architecture.md`. The Elixir-specific rules:

- A NIF is the shell of last resort: only when the BEAM cannot meet the
  requirement. Never a default; never for IO the BEAM already does.
- Data in, data out. Marshal Elixir terms into the call and back; do not
  push domain logic across the edge.
- Opaque handles for native state. Return a reference the caller passes
  back; never reach into its internals from Elixir.
- Errors cross as values, never as exceptions across the NIF boundary.
  A NIF that crashes the VM is a bug.
- A NIF that may run long runs dirty (`:dirty_cpu` or `:dirty_io`), and
  the loader declares it. A NIF that blocks a clean scheduler is a
  finding.

## Formatting and layout

- `mix format` is authoritative; touched files pass
  `mix format --check-formatted`.
- One function clause per line in the head; long parameter lists wrap.
- Keep functions short. Approaching a size limit means two
  responsibilities; split by phase or domain, do not shave lines.

## Testing

- ExUnit under `test/`, mirroring `lib/`. `describe` blocks name the
  behavior; the test name completes the sentence.
- Test the pure core by calling it: data in, pattern-match the tagged
  tuple out. No mocks.
- Test the shell with `start_supervised` so each test gets a fresh
  process tree and a crashed worker never leaks. Assert on the public
  API (calls and casts), not the internal state.
- Property-based tests (via StreamData or the project's generator
  library) on non-trivial core logic (a query DSL, a transformation
  pipeline, a stateless computation). A property failure is a real bug;
  pin the shrunk case as a regression.
- Edge cases are the point: nil, empty, single, boundary sizes, a
  malformed payload that must reject as data, a process that crashes and
  restarts.

## What to avoid

- The process dictionary as hidden state. Use a GenServer.
- Side effects inside a pure module. If a function needs the world, it
  belongs in the shell.
- Mocking a GenServer to reach a branch. Move the decision into the core.
- A NIF where the BEAM already serves. Default to Elixir; reach for C or
  Zig only at the measured edge.
- Heavy dependencies. Prefer small, well-understood libraries and
  explicit composition; add a dependency only when the project already
  uses it or a requirement clearly demands it.

## Before writing code

Work the problem in data terms first, then write:

1. State the problem as data (maps, structs, tuples) and how it changes
   as events.
2. Sketch the data shapes with example literals for input, state, and
   output.
3. Design the pure core: the functions, their clauses, their inputs and
   outputs, how they compose.
4. Design the shell: which processes hold which state, the supervision
   tree, the restart strategy, the messages they exchange.
5. Check this file: module and function naming, pattern matching, tagged
   tuples, pipe and `with` use, supervisor structure.
6. Provide sample data and tests for the core functions.

Think it through, then write; do not narrate the checklist in the code.

## Public-facing text

- Never describe code as hand-written or hand-rolled in user-facing
  docs, docstrings, or commit and changelog lines.

---

*Grounded in the Elixir Style Guide, the Elixir Getting Started guide,
the OTP design principles, and the standard library.*
