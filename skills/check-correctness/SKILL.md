---
name: check-correctness
description: Review dimension for logic bugs, boundary edges, and arithmetic defects over one module shard. Invoked by reviewer agents.
user-invocable: false
---

# check-correctness

Role: review the shard for behavior that is simply wrong.

Failure model: the code does not do what the surrounding system needs on
some input or interleaving; the bug that ships is the edge the happy
path hides.

## Look for

1. **Arithmetic and indexing.** Off-by-one in a loop bound; signed and
   unsigned mismatch; integer overflow on an accumulator or a size
   computation; a count flowing into indexing without a bound check;
   float precision loss in a long-running accumulator; a division whose
   divisor can be zero.
2. **nil, null, empty, and boundary inputs.** Empty collection, single
   element, zero, negative, a missing map key, an empty string, an
   Option or Result unwrapped without the missing case. The happy path
   is not the test; the boundary is.
3. **Order and composition sensitivity.** A transformation chain that
   produces a different result when applied in a different order; a
   filter or reduction that mishandles the empty case; a sort that is
   not stable where the caller depends on stability; a set versus list
   confusion that drops or duplicates elements.
4. **Concurrency and interleaving.** A callback that fires mid-mutation;
   a message that arrives between a decision and its effect; a shared
   mutable value read and written without the synchronization the shell
   owns; a future or task awaited in the wrong order. The pure core
   avoids this by construction; the shell is where it lives.
5. **Protocol and state machine errors.** A case, match, or switch with
   a missing arm; a state transition that skips an intermediate state
   the contract requires; an error code outside the documented catalog;
   a sentinel value stored where the domain expects absence.
6. **Error-path correctness.** A recovery path that swallows the failure
   and returns a wrong success value; an error mapped to the wrong
   diagnostic; a cleanup path that skips a step the success path runs.
7. **Native edge dispatch.** A wrapper that calls the wrong native
   function, passes arguments in the wrong order, or misreads the return
   shape. The lifetime and ownership of the handle are check-memory's;
   the dispatch logic is here.

## Ignore here

Pure style, naming, comment debt (check-style). Module boundaries,
dependency direction, duplication (check-factoring). Conformance to the
spec or an ADR (check-conformance). Memory safety, ownership, leaks
(check-memory). Untrusted input reaching the unsafety (check-security).
Cost on or off a hot path (check-performance).

## Severity

- `:high`. User-triggerable wrong output, data corruption, a crash on
  reachable input, or a broken contract the caller depends on.
- `:medium`. A narrow edge a typical caller will not hit, or a wrong
  result only on an uncommon path.
- `:low`. A latent defect with no current trigger, or a correctness
  technicality.

## Level

`:correctness`. Correctness findings land in the first editor wave,
before any factoring or style work.

## Boundaries

Owns: behavior that is simply wrong on some input or interleaving.
Siblings: check-factoring owns where the code lives; check-style owns
how it reads; check-conformance owns whether it matches the spec;
check-memory owns the lifetime and ownership of native state;
check-security owns untrusted input reaching the unsafety;
check-performance owns the cost.

## Return

An EDN vector of finding maps (shape in
`skills/shared/references/review-model.md`), one per defect, each with
`:file`, `:evidence` quoting the location and the breaking input, and a
`:suggestion` sketching the fix or a failing test. Do not bundle
defects. When the shard has none, return exactly:

```
NO FINDINGS
```
