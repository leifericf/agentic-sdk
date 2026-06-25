---
name: check-performance
description: Review dimension for hot-path allocation, budget breaks, and needlessly unbounded work. Invoked by reviewer agents over one module shard.
user-invocable: false
---

# check-performance

Role: review the assigned shard against the project's performance targets.

Failure model: the code is correct but breaks a real-time or budget
commitment, allocates on a hot path, or does work that grows worse than
linearly with input the caller controls.

Performance targets are project-specific. Read the design docs and the
descriptor for the project's budgets (frame time, callback latency,
query cost, scan throughput) before sweeping. What follows is the sweep
pattern, not the budget.

## Look for

1. **Allocation on hot paths.** A real-time callback (audio, video,
   input) or a per-frame function that allocates on the steady path: no
   incidental allocation, no internal collection growth, no formatting
   inside the loop. A per-keystroke query path must not allocate per
   call beyond the result vector. Any allocation in these paths is a
   finding.
2. **Locks on hot paths.** A lock held across an unbounded operation on
   a real-time or per-frame path. Cross-thread communication on a hot
   path belongs on a lock-free queue or an atomic, not a mutex.
3. **Arithmetic that defeats SIMD or the hardware.** Inner loops over
   sampled data written as scalar code when the platform's vector type
   would do; mixed precision inside a hot loop that forces a conversion
   per iteration. Vectorization is not always the right answer; the
   choice must be deliberate, and a benchmark wins the argument.
4. **Unbounded work from unbounded input.** A scan that processes a
   whole set synchronously without yielding; a decode that loads the
   whole payload before streaming; an analysis whose runtime grows
   worse than linearly with input length; a layout that recomputes from
   scratch on every change.
5. **Query and index efficiency.** A query that scans the whole set
   when an index would do; a filter that re-realizes a lazy sequence on
   every access; a lookup that walks a list when a set or map would do;
   a sort or projection recomputed when the input has not changed.
6. **Waste in a render or diff pipeline.** A per-frame allocation in a
   diff path; a uniform or buffer update that re-uploads static data; a
   descriptor or command buffer rebuilt when a single binding changed.
7. **Throughput on a scan or batch path.** A scan that reads the whole
   payload to compute a value the header would yield; a batch that
   reworks items whose input has not changed (a content hash is the
   gate); a batch that holds a shared lock for its whole duration.

## Ignore here

Pure style (check-style). Pure factoring (check-factoring), unless the
factoring is what puts logic on a hot path. Micro-optimizations on code
not on a hot path. Correctness (check-correctness).

A finding needs a hot path identified (cite the call site or the loop),
a measurable or mechanistic argument for why the current code is slow,
and where possible a sketch of the alternative.

## Severity

- `:high`. A finding that breaks a real-time contract (a glitch, a
  dropped frame) or a documented budget target.
- `:medium`. A finding that degrades a hot path without breaking a
  contract.
- `:low`. A finding that affects only cold paths.

## Level

`:factoring` for most findings (a performance fix is usually a
structural change); `:correctness` for real-time-safety findings,
because they ship as user-visible glitches.

## Boundaries

Owns: the cost of the code against the project's budgets. Siblings:
check-correctness owns whether it is right; check-factoring owns where
it lives; check-design owns the UI-experience targets when the cost is
born by the UI data path specifically.

## Return contract

An EDN vector of finding maps (shape in
`skills/shared/references/review-model.md`), one per defect, each
naming the hot path and the budget it threatens. When the shard has
none, return exactly:

```
NO FINDINGS
```
