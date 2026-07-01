# Clojure style: the checkable standard

Applies to all Clojure in this project: the pure core, the imperative
shell, the native wrappers, tests, and build or tooling scripts.
`check-style` applies this file; `write-clj` writes to it. This is the
normative coding standard; where a general Clojure source disagrees,
follow this file.

## What the standard optimizes for

- **Simple over easy.** Simple means unbraided: one concern, reasoned
  about on its own. Easy means familiar or near at hand. Prefer the
  simple artifact even when less familiar to write.
- **No complecting.** Do not interleave concerns that could stand apart:
  state with time, data with behavior, logic with effects, the domain
  with its storage or rendering. Code that braids them cannot be
  reasoned about in pieces.
- **Values, not mutation.** A value is immutable information. An
  identity is a named succession of values over time; its state is the
  value at a point in time. Model change as new values, never as
  in-place mutation.
- **Data-oriented.** Plain maps, vectors, and sets carry information at
  the edges and through the core. Entities are data; transactions are
  data; view-specs are data; events drained from native are data; the
  boundary contract on every native call is data.

## Core vocabulary

- **Simple / easy:** unbraided and objective, versus familiar and
  relative to a person. Choose simple.
- **Complect:** to interleave distinct concerns so they no longer
  separate. The thing to avoid.
- **Value / identity / state / time:** immutable information; a named
  series of values; the value now; the succession of values. Make time
  explicit by producing new values.
- **Description, not instruction.** Declare what an entity, a spec, or
  an operation chain contains; the pure functions decide how to apply
  it. Do not instruct step by step when a data description suffices.
- **Functional core, imperative shell:** the load-bearing split. The
  contract lives in `skills/shared/references/architecture.md`.
  Deciding is pure data transformation; doing is the shell that adapts
  inputs, calls the core, and applies the result as effects.

## The spec is JVM Clojure

- Canonical JVM Clojure defines correct behavior. Follow the community
  Clojure style guide where a norm exists; do not invent house style.
- A public surface is ordinary Clojure: functions return Clojure values
  and redefining a function changes its behavior at the REPL. Match that
  expectation; surprising semantics at the public surface are findings.
- Java 22+ for the finalized foreign-function and memory API where a
  native edge uses it. JVM interop in the shell is fine (a peer
  database API, a window hook); do not let it leak into the pure core.

## Functional core, imperative shell, native wrappers

The spine separates deciding from doing, with a native edge for what
must run on the metal. This is load-bearing structure, not a preference
(see `skills/shared/references/architecture.md`).

- **Pure functions.** Functions that take data and return data, do no IO,
  never shell out, never transact, never hold a clock, thread, or atom.
- **Effectful functions.** Persistence wiring, OS integration, lifecycle
  and composition — functions that adapt inputs to data, call pure
  functions, and apply the result as effects. Marked with a trailing `!`.
- **Native wrappers.** Each a thin layer over a native call. Marshal
  data in, marshal data out, pass handles back and forth. No domain
  logic; the pure functions decide what to call.
- **Namespaces are domain-based.** A namespace is named for the domain
  it owns (a single concept: the cache, the compiler, the source), not
  for the pure/effectful split. A namespace holds both pure functions and
  the `!`-marked effectful functions that drive them; the split is a
  function-level discipline, not a namespace boundary. Do not split a
  domain into a pure namespace and a shell namespace.

Most tests target the core directly: data in, data out. Shell tests are
fewer and integration-style (a real scratch store, a real scratch
cache). A shell branch a test wants to reach is a decision that belongs
in the core: move it, do not mock the shell. Do not push an effect into
the core because a library makes it easy.

## Persistence-specific rules

The persistence module owns the connection (or store handle) and is the
only place that calls the transact, query, and db APIs against a live
connection.

- **Build transaction data in pure functions.** A pure function takes a
  domain event and returns a vector of transaction maps. The shell
  transacts. Never call the transact API from a pure function.
- **Schema is data.** Schema installation lives as a data structure;
  tests assert it installs cleanly on an empty store and migrates from
  the previous release's schema.
- **History is a feature.** When the store keeps history, undo, as-of,
  and since-query behaviors depend on it. Tests exercise them: retract a
  fact, query as-of the prior point, assert the entity shape. A history
  regression is a real bug.
- **No business logic in queries.** The query pulls data; computation on
  the pulled data lives in the pure core. A complex predicate in a query
  rule is usually a factoring finding: pull more, compute in Clojure.

## Tooling and workflow

- `deps.edn` and the Clojure CLI for dependencies and configuration, not
  Leiningen. Module paths are declared in the top-level `deps.edn`; each
  module directory has its own `src/` and `test/`.
- REPL-driven development is the primary loop: evaluate small forms,
  inspect values, adjust, repeat. The pure core is built for this: no
  setup ceremony, data in and data out.
- Keep forms well-structured for structural editing: balanced parens,
  idiomatic indentation, trailing parens gathered.
- Prefer `tap>` over `println` for inspecting values during development;
  it reaches a tap listener without disturbing control flow.
- Dynamic namespace manipulation (`require`, `in-ns`, `refer`) is for
  the REPL, never inside functions or production paths.
- `clj-kondo` for static lint; `cljfmt` or `zprint` for formatting. A
  rule that fires on violation beats prose; configure these to enforce
  this file's naming and layout conventions.

## `ns` form and namespace structure

- kebab-case namespaces under the project root, matching the module
  directory. Exactly one namespace per file, one file per namespace.
  Public API at the top, private helpers below.
- Name a namespace for the domain it owns — a single concept (`cache`,
  `compiler`, `source`) — not for the pure/effectful split. A namespace
  holds both its pure and its `!`-marked effectful functions.
- Start each file with one `ns` form: `:require` before `:import`.
  `:require :as` over `:refer [...]` over `:refer :all`; avoid `:use`.
  Sort entries alphabetically.
- Use idiomatic aliases and the same alias for a namespace across the
  project: `str` for `clojure.string`, `set` for `clojure.set`, `io` for
  `clojure.java.io`.
- A standalone library module must hold only pure functions and require
  nothing effectful or native. A violation is a factoring finding.

## Formatting and layout

- 2-space indentation, no tabs. Indent form bodies by 2 spaces.
- Align `let` bindings and map values vertically:

  ```clj
  (let [sample-rate 44100
        frame-count  (* duration sample-rate)]
    ...)

  {:entity/id    (java.util.UUID/randomUUID)
   :entity/path  "/path/to.dat"
   :entity/rate  120.0}
  ```

- A space between a form and a preceding sibling, none after an opening
  bracket: `(foo (bar baz) quux)`.
- Keep lines short enough for side-by-side viewing; 80 columns is the
  target, 120 the ceiling.
- Gather trailing parens. One blank line between top-level forms, except
  tightly related defs. Avoid blank lines inside a `defn` body, except
  to group `cond` clauses.
- No commas in sequential literals: `[1 2 3]`, not `[1, 2, 3]`. Commas
  in maps are optional; keep them consistent within a map.

## Naming

- Predicates end in `?` (`valid?`, `ready?`). Effectful or mutating
  functions end in `!` (`scan!`, `recompile!`). Dynamic vars intended
  for rebinding wear earmuffs (`*config*`).
- Conversions use `->`, not `to`: `event->tx-data`, `spec->draw-calls`,
  `sample->row`. Unused bindings are `_` or `_`-prefixed (`_ctx`).
- Native boundary types are keywords with exact native spelling (`:i64`,
  `:f64`, `:void`); compound types are vectors (`[:slice :const :u8]`);
  handle types are keywords matching the native type (`:Frame`,
  `:Renderer`). Keep that vocabulary exact.
- Persistence attributes are `:<entity>/<field>` keywords
  (`:sample/rate`, `:collection/name`); keep the namespace part stable
  across the schema and the queries.
- Protocols, records, and types are `CapitalCase`. Entities are modeled
  as plain maps with a discriminator keyword, not as records. Reach for
  `defrecord` only for protocol dispatch or a Java interop boundary,
  never to imitate a class.

## Data and domain modeling

- Default to plain maps with keyword keys for entities, options, and
  configuration. Consumers query data; tests assert on data.
- Reach for `defrecord` only for protocol dispatch, a Java interop
  boundary, or a measured hotspot, never to imitate a class.
- Model state as open data: accept and produce EDN-shaped values, keep
  the domain independent of any storage or rendering. The domain never
  knows about the store; the view-spec never knows about the renderer.
- Use `clojure.spec` or `malli` sparingly, at boundaries and for
  critical invariants (the native signature vector, the operation chain
  shape, the view-spec shape), not across internal shapes context
  already makes obvious.

## Collections and sequences

- Sequence library first: `map`, `filter`, `reduce`, `into`, `group-by`,
  `keep`, `frequencies`, `some`, `map-indexed` over manual
  `loop`/`recur`.
- `mapv`, `filterv`, `reduce-kv` when a vector is wanted or a map is
  iterated; `vec`, not `(into [] ...)`.
- Reach for transducers (`transduce`, `comp` of `map`/`filter`) when
  data volume is large or the transformation should be decoupled from
  its source and sink.
- Nil punning: `(when (seq coll) ...)`, not
  `(when-not (empty? coll) ...)`. Sets as predicates where natural:
  `(filter #{:a :b :c} tags)`.
- Vectors are the default collection in an API; maps carry entities and
  options; lists are for code and the rare data case.

## Functions and APIs

- Keep functions focused and readable. Factor a helper when one is doing
  too much, not to hit a line count.
- Avoid more than three or four positional parameters; carry the rest in
  an options map (`{:keys [...]}`). Use multi-arity for defaulting, with
  smaller arities calling the largest, ordered fewest to most.
- Use `:pre`/`:post` for critical invariants at public boundaries (the
  domain API, the persistence entry points), not everywhere.
- Errors: throw `ex-info` with a rich data map (the entity id, the
  offending operation, the constraint name) at boundaries; return
  explicit error values inside the pure core when callers branch on the
  outcome. Pick one per area; do not mix arbitrarily.
- Log at the edges, in the shell, with `clojure.tools.logging`; log
  readable Clojure (`pr-str`) so a line pastes back into the REPL. No
  logging in core pure functions, no test that depends on a log effect.
- Polymorphism: protocols for closed, type-based dispatch;
  multimethods for open, data-driven dispatch (on `:op`, `:kind`). Avoid
  `class` plus `cond` where either expresses the intent better.

## State, identity, and concurrency

- Reference types model identities and their state over time. Keep them
  at the edges: the store connection, the native handle registry, the
  loaded native library set, the app's component map.
- Atoms for uncoordinated synchronous updates to one identity, with a
  pure function under `swap!`. Refs and `dosync` for coordinated updates
  across identities (rare; usually the database transaction is the
  coordination point). Agents for asynchronous ordered updates. Dynamic
  vars for genuine dynamic configuration only, never as general mutable
  state.
- Core functions neither capture nor mutate a reference; they take
  values and return values. Design so state can be inspected and
  reproduced.

## Macros

- Write the function first. A macro is for genuine syntactic
  abstraction, never for a single call site or to save characters.
  Prefer a higher-order function where one suffices.
- A native-edge macro and any project-local macros stay thin over the
  data functions. A user must be able to reach the same result through
  the underlying functions without the macro.
- Keep macro bodies small and data-oriented; deep macro logic is hard to
  reason about.

## Control flow idioms

- `when` for a one-armed `if`; `if-let`/`when-let` over `let` plus
  `if`/`when`; `if-not`/`not=` over a wrapped `not`.
- `cond` with short paired clauses and `:else`; `condp` when only the
  argument varies; `case` for compile-time constants
  (`case (:op event) :added ... :removed ...`).
- Threading macros over deep nesting:

  ```clj
  (->> (items coll)
       (filter #(contains? (:tags %) :a))
       (map :rate)
       (frequencies))
  ```

## Events drained from native are data

- The shell drains a queue of events from the native side per frame
  (file-watch events, completion events, resize events). The events
  arrive as Clojure data: `{:event/kind :watch/added :event/path "..."}`
  or similar.
- Resolve events in the core (hit-test to action). Apply actions in the
  core (pure functions). Persist changes in the shell. Compute the next
  view in the core. Render at the edge. Never short-circuit the loop;
  the data flow is what makes the architecture testable.

## Tests

- `clojure.test` under the test tree mirroring the namespace layout of
  `src`, with `deftest` names that describe the behavior. See
  write-tests for surface selection.
- Test the pure core directly; never mock. Effects run against real
  scratch state: a scratch store, a scratch native cache.
- Many tests for the core, fewer for the shell, integration-style for
  native. Reach for a property library on non-trivial core logic (a
  query DSL, an operation chain evaluation, a view-spec computation); a
  property failure is a real bug, fixed at the source with the shrunk
  case pinned as a regression.
- Edge cases are the point: nil, empty, single, boundary sizes, unsigned
  values beyond signed JVM bounds, schema migration from the prior
  release.

## What to avoid

- Imperative index-walking where sequence functions suffice.
- `def` inside functions; vars as hidden mutable state; business state
  in a singleton instead of values passed through the call chain.
- Macros where functions suffice; tacit `comp`/`partial`/`#()` chains
  that obscure intent.
- Writing Java in Clojure: mutable Java collections in the core, heavy
  `new`/`set!`. Native calls are the shell's and the wrappers' job, kept
  behind the boundary; the core stays plain data.
- Heavy dependencies. Prefer small, well-understood libraries and
  explicit composition; add a dependency only when the project already
  uses it or a requirement clearly demands it.

## Before writing code

Work the problem in data terms first, then write:

1. State the problem as entities (maps, vectors, sets) and how they
   change over time as events.
2. Sketch the data shapes with example literals for input, state, and
   output.
3. Design the pure core: the functions, their inputs and outputs, how
   they compose.
4. Design the shell: the IO boundaries (transactions, native calls,
   watchers) and which identities hold state.
5. Check this file: namespaces and aliases, formatting and naming, maps
   over records, error handling, control flow, sequence use.
6. Provide sample data and tests for the core functions.

Think it through, then write; do not narrate the checklist in the code.

## Public-facing text

- Never describe code as hand-written or hand-rolled in user-facing
  docs, docstrings, or commit and changelog lines.
