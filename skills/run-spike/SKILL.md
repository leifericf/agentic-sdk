---
name: run-spike
description: Run a risk spike: name the load-bearing unknown, time-box it, build a throwaway harness that exercises the seam at and beyond expected load, decide go or no-go or mitigate, feed the outcome forward.
user-invocable: false
---

# run-spike

A spike buys knowledge, not a feature. Run one when a load-bearing
unknown sits in front of real work and you cannot plan past it
honestly. Front-load the biggest unknowns, the ones at the seams: the
native edge (a C ABI, NIF, or foreign-function boundary that may leak
or crash under churn), a real-time budget a renderer or audio path
must hit, a concurrency model the single-threaded path hides.

A spike learns; a feature ships. When the answer is known and the work
is to build the planned thing, that is `implement-change`. When the
work is to break existing code, that is `adversarial-test`. A spike has
no user; its deliverable is a decision plus, sometimes, a harness worth
keeping.

## Procedure

The discipline, in order. Do not skip steps.

### 1. Name the unknown and the question

Write down, in one or two sentences each:

- The load-bearing unknown. The thing that, if it goes the wrong way,
  changes the plan.
- The question the spike must answer, phrased so the answer is a fact,
  not an opinion. "Does a hundred thousand native-handle create and free
  cycles return allocation to zero?" not "Is the native edge good
  enough?" A fuzzy question produces a fuzzy decision.
- The decision the answer unblocks: which planned work waits on this,
  and what the go, no-go, and mitigate branches each mean for it.

State all three to the maintainer before building anything. The spike
is scoped wrong if you cannot.

### 2. Time-box it

Set a budget up front: hours, or a fixed number of harness iterations.
A spike runs until it answers the question or the box closes, whichever
comes first. A box that closes with no clear answer is itself a
finding: the seam is harder to characterize than expected, and that
goes in the write-up. Do not let a spike slide into building the
feature because the harness started to look real.

### 3. Build a standalone, throwaway harness

The harness is scaffolding, not product. Build it to be discarded (step
7 decides if any earns a place). Keep it isolated: its own namespace,
module, or file under a spike path, no edits to the modules it probes,
scratch state under a tmp dir.

Exercise the seam at and beyond expected load. The point is to find the
wall, not confirm the happy path.

- **Lifetime under load.** Create and free a native handle N times in a
  tight loop, N past any realistic use. Watch for the wall: a slowdown,
  a crash, a climbing resident set.
- **Concurrency.** Parallel threads issuing native calls at once, to
  surface races the single-threaded path hides.
- **Large payloads.** Big buffers and owned slices crossing the
  boundary, to test the copy path and large allocations.
- **The leak lane.** After any sequence, assert native allocation
  returns to zero via the handle-addressed counter (`write-tests` owns
  the pattern). A leak at the boundary is the reason the spike exists;
  instrument for it from the first iteration.
- **Real-time budget.** Where a renderer or audio path has a frame or
  call budget, measure per-call wall time on stated hardware at a
  stated load; assert under budget. Record the hardware and the load;
  the number is meaningless without them. Turn the load past the target
  until the budget breaks, and report where.

Where an API does not exist yet, state the contract you code against
(the boundary contract in `references/architecture.md`, the handle
lifecycle) and build the harness to it. Never invent an API and present
it as real; if the spike must stub the seam, say so, and say what the
stub assumes.

### 4. Document failure modes, limits, and workarounds

As the harness finds walls, write each down plainly:

- The failure mode: what broke, the smallest sequence that broke it,
  the observed symptom (crash, leak count, frame time, validation
  error).
- The limit: the number where it broke (iterations, thread count,
  payload size, load, milliseconds).
- The workaround, if any: what made it work, and what that costs (batch
  frees, cap concurrency, pre-size the buffer). A workaround with no
  cost is suspicious; find the cost.

This catalog is the spike's evidence. The decision rests on it.

### 5. Decide: go, no-go, mitigate

Turn the evidence into one call:

- **Go.** The seam carries the load. The planned work proceeds as
  written. Record what "carries the load" meant and the limits you
  proved, so a later regression has a baseline.
- **No-go.** The seam does not carry the load and there is no
  acceptable workaround. The plan changes. Name the alternative the
  project now takes.
- **Mitigate.** The seam carries the load only with a workaround. Spell
  out the workaround as a constraint downstream code must honor, and
  route it (step 6) so the constraint is not lost.

### 6. Feed the outcome forward

A spike that settles a real choice produces a record. Two homes, both
may apply:

- **The decision becomes an ADR.** When the spike settles a choice a
  reasonable person could have called either way (go versus no-go on a
  dependency, one backend over another, a mitigation that constrains
  future code), invoke `record-decision`. The ADR holds the why and the
  evidence; if the mitigation changes how code is written, route the
  rule to its reference too. A spike that only confirmed the obvious
  does not need an ADR; say so and stop.
- **The constraint goes to the right doc.** Limits and workarounds
  downstream code must honor go where that code's author will see them:
  the relevant reference, the boundary contract, or the module brief.

### 7. Keep or discard the harness deliberately

The harness is throwaway by default. At the end, make the call
explicitly; do not leave it to rot in a spike path.

- **Earns a place: a `Tests:` commit.** If the harness is a real
  regression guard (the leak-lane assertion, the budget check), promote
  it: move it into the proper test surface per `write-tests`, name it
  for what it guards, land it as a `Tests:` commit.
- **Throwaway: thrown away.** If it was scaffolding to answer the
  question and nothing more, delete it. Do not comment it out, do not
  leave it disabled. jj holds the history if you ever want it back.
  Dead harness code is a finding the next reviewer files.

Commit per `write-commit` (jj, single line, category first). The ADR
commit and the kept-harness `Tests:` commit are separate.

## Boundaries

Owns the learning spike and its harness. Building the planned thing
once the unknown is resolved is `implement-change`; the write standard
for any kept harness is `write-tests`; turning a settled choice into a
dated record is `record-decision`. Does not edit product modules to
make the harness fit; it probes them from outside.

## Return

`spike <name>: <go|no-go|mitigate>, <f> failure modes, <w> workarounds, ADR <NN|none>`.
