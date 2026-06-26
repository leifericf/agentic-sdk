---
name: check-memory
description: Review dimension for ownership, lifetimes, leaks, and GC safety over one C or Zig module shard. Invoked by reviewer agents when the descriptor activates :memory.
user-invocable: false
---

# check-memory

Role: review the assigned C or Zig module for memory defects.

Failure model: an allocation outlives its owner, a borrow outlives its
backing memory, or a GC-visible pointer is held across an allocation
point without a guard; the bug that ships is the path that skips a free
or the scanner happens to hide.

Active only when the descriptor's `:languages` includes `:c` or `:zig`.
Read the ownership rules in
`skills/shared/references/architecture.md` (the C and Zig sections) and
any project-specific known-pitfalls note before sweeping.

The bug class splits by language.

## Look for

### C (GC or manual ownership)

1. **GC windows.** Any GC-visible pointer or raw buffer held across an
   allocation point without a pin or a depth guard. Check pin and unpin
   pairing on every path, including the error path (the save-stack
   assertion often fires only in debug builds).
2. **Ownership violations.** `free` on a GC-owned value; a GC value
   stored in host-owned memory the tracer cannot see; a peek or get
   result freed or retained past the next collection; a take result
   leaked.
3. **realloc misuse.** `p = realloc(p, n)` without a temp; the old block
   leaks when realloc fails.
4. **Error-path leaks.** For each early return or longjmp out of a
   function that allocated host memory: what frees it?
5. **Write-barrier gaps.** Direct stores into old-generation objects that
   bypass the barrier (mutation outside the provided mutators).

### Zig (explicit allocators, no GC)

1. **Missing defer or errdefer.** Every heap allocation must have a
   matching free reachable on every path. `defer` for the success path;
   `errdefer` for the partial-construction path before ownership
   transfers to the caller. Check each early return, each `try`, each
   error exit: what frees the buffers allocated above it?
2. **Wrong allocator for the phase.** A per-node scratch allocated from
   the execution allocator (or vice versa); a value meant to outlive
   parsing allocated from the parse arena; a long-lived entry on a
   short-lived arena. Match the allocation to its phase.
3. **Slices and pointers outliving backing memory.** A slice returned
   from or stored past the `deinit` of the allocator that produced it; a
   sub-slice retained after the parent is freed; a value that aliases a
   freed source buffer.
4. **init and deinit pairing.** Every type with an init or alloc
   constructor needs a deinit that frees exactly what it owns, called on
   every path. Double-free (freeing a borrowed slice the owner also
   frees); missing-free (a field allocated in init not released in
   deinit).
5. **Allocator passed explicitly.** No hidden global allocator; the
   allocator is a parameter threaded through. Flag any subsystem that
   reaches for a package-level allocator instead of the one its caller
   passed.
6. **Alignment and realloc.** An `@alignCast` without a real guarantee;
   a realloc whose result is dropped on failure (the old block leaks).

## Ignore here

Style, naming, factoring (other dimensions). Correctness of the logic
apart from the memory defect (check-correctness). Security reachability
of the defect from untrusted input (check-security). Performance cost of
the allocation pattern (check-performance).

A finding needs file and line and the specific pointer or path. Suggest
a stress or leak-detection repro in `:suggestion` when you can construct
one (a GC-stress flag, an address-sanitizer run, a leak-detecting test
allocator that fails the test on any leak).

## Severity

- `:high`. Anything user-triggerable that corrupts memory, reads or
  writes out of bounds, or uses freed memory; an unbounded leak.
- `:medium`. A bounded leak; a missing free on an uncommon path.
- `:low`. A latent defect with no current trigger.

## Level

`:correctness`. Memory findings land in the first editor wave.

## Boundaries

Owns: the ownership, lifetime, and leak discipline of native state.
Siblings: check-correctness owns the logic; check-security owns whether
untrusted input can reach the defect; check-performance owns the cost of
the allocation pattern; check-conformance owns the edge contract the
handle catalog depends on.

## Return

An EDN vector of finding maps (shape in
`skills/shared/references/review-model.md`), one per defect, each with
the allocation, the path, and the repro. When the shard has none,
return exactly:

```
NO FINDINGS
```
