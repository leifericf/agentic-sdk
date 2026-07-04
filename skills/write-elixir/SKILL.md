---
name: write-elixir
description: Recipe for writing Elixir, pure functions in modules as the core and GenServer plus OTP as the shell, supervision trees, and NIF discipline for the C and Zig edge. Invoked when writing Elixir for the project.
user-invocable: false
---

# write-elixir

Write Elixir for the project. The standard is
`skills/write-elixir/references/elixir-style.md` (read it first). The
architecture it implements is Functional Core / Imperative Shell; the
Elixir expression of that split, and the native boundary contract, live
in `skills/shared/references/architecture.md`. Placement follows the
module map (the `:architecture :modules` entry in the descriptor). The
why behind the constraints is the ADR log: scan it before designing
against an unexplained rule.

The Elixir expression of the split maps cleanly onto OTP. The pure core
is pure functions in modules: they take data (maps, structs, tuples) and
return data, do no IO, send no messages, hold no state. Pattern matching
and the immutability guarantee do the work the GC does in C and the
allocator does in Zig. The imperative shell is GenServers and OTP:
processes that hold state, receive messages, talk to the disk and the
network. The shell calls the pure core from inside `handle_call`,
`handle_cast`, and `handle_info`, and applies the return as the new
process state plus any side effects.

## Procedure

1. **Place it.** Find the owning module from the module map. Pure logic
   goes in a plain module of functions; a process that holds state,
   receives messages, or touches the world goes in a GenServer (or the
   fitting OTP behaviour) under the shell. A function that needs a PID
   to make sense does not belong in the core.
2. **Elixir discipline.** The load-bearing rules:
   - **Pure core has no process identity.** A core function does not know
     the PID it runs in; it does not send or receive, it does not read
     or write the process dictionary, it does not consult the clock.
     Testing the core is calling it: data in, data out, no setup
     ceremony.
   - **GenServer and OTP are the shell.** State, timers, IO, and
     messaging live in GenServers, Agents, Tasks, and the supervisors
     that own them. The shell calls the core from a callback and applies
     the return: the new state from the pure result, the side effects
     the result describes. The shell carries no logic of its own; it
     switches on the tagged tuple the core returns.
   - **Supervision trees own lifecycle.** The shell is a tree of
     supervisors and workers; the restart strategy (one_for_one,
     one_for_all, rest_for_one) is explicit and chosen for what a crash
     invalidates. Pure functions never start or stop processes. A worker
     and its supervisor land together; a worker without a place in the
     tree is incomplete.
   - **Errors as tagged tuples in the core; let-it-crash at the edge.**
     The core returns `{:ok, value}` or `{:error, reason}` tuples a
     caller branches on with `with`; it does not raise. The shell raises
     on programmer error and lets the supervisor restart on runtime
     failure, because the supervision tree is the recovery strategy.
   - **NIF boundary discipline.** When a NIF calls C or Zig, the native
     boundary contract in `skills/shared/references/architecture.md`
     applies unchanged: data in, data out, opaque handles, explicit
     lifetime, errors as values. A NIF is the shell of last resort:
     only when the BEAM cannot meet the requirement, never as a default.
     A NIF that blocks the scheduler runs dirty, and that decision is
     written down next to the loader.
3. **Failure model.** The core returns tagged tuples for expected
   failure and raises only for programmer error (a violated
   precondition the caller controls). The shell trusts the supervision
   tree for runtime failure: a crashed worker restarts into a known-good
   state, and the tree's restart intensity bounds a crash loop. Untrusted
   input (bytes from the network or disk, a payload that crosses the
   NIF edge) is validated before it reaches the core and rejected as
   data when malformed.
4. **Verify like the lanes.** The project's cheap lanes from the
   descriptor: `mix format --check-formatted`, `mix compile --warnings-as-errors`,
   `mix test`. For NIF or native-edge code, run the integration lane that
   builds the native side, loads it, and calls it with both a known-good
   and a malformed input. If you wrote process or supervision code, run
   the suite that starts the tree and tears it down, so a leak or a
   missing child fails the test.

## Tests-first

Tests are ExUnit under `test/`, mirroring `lib/`. `describe` blocks name
the behavior; assertions use the `assert` match forms. Test the pure
core by calling it: data in, pattern-match on the tagged tuple out. Test
the shell with `start_supervised` so each test gets a fresh process tree
and a crashed worker never leaks across tests. A shell branch a test
wants to reach is a factoring finding: move the decision into the core,
do not mock the process. See write-tests for surface selection and the
teeth every assertion needs.

## Boundaries

Owns: the Elixir module, its place in the core or shell split, and the
tagged-tuple contracts that cross the seam. Cites: the architecture
contract in `skills/shared/references/architecture.md` for the
Functional Core / Imperative Shell split, the OTP-as-shell mapping, and
the native boundary contract; the module map for placement; the ADR log
for the why. Siblings: write-tests owns the test surface; write-c and
write-zig own the native side of a NIF edge; write-prose owns the prose
standard. The core never knows the shell exists; a NIF wrapper stays
thin.

## Comments and public text

The Elixir standard library is the calibration target: inline comments
are rare one-liners, and documentation lives in `@moduledoc` and `@doc`.
The full budget lives in `references/elixir-style.md`; in short: comment
only the why (a restart-strategy choice, a NIF scheduling decision,
another constraint the code cannot say), never the what; a comment block
is at most three lines; file density above one line per fifty code lines
is a finding; no banners, no commented-out code, no change narrative.

`@moduledoc` and `@doc` on every public module and function, describing
what the function returns and the shape of inputs that require it, not
the mechanism. `@spec` on public functions whose contract is not obvious
from the name and the pattern.

Public-facing text rule: never describe code as hand-written or
hand-rolled in docs, docstrings, or changelog lines, and never carry an
internal process identifier (a phase, task, slice, or run label) into a
comment or commit. See write-prose.
