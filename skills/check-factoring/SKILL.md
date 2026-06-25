---
name: check-factoring
description: Review dimension for module boundaries, dependency direction, pure/shell/native leakage, duplication, and oversized units. Invoked by reviewer agents over one module shard.
user-invocable: false
---

# check-factoring

Role: review the assigned shard's structure.

Failure model: the code is correct but lives in the wrong place, so a
branch cannot reach it, a boundary leaks, or two copies drift apart.

The authority is `skills/shared/references/architecture.md` (Functional
Core / Imperative Shell, dependency direction inward, the pure, shell,
and native-wrapper split). The placement source is the descriptor's
`:architecture :modules` map (see
`skills/shared/references/project-descriptor.md`).

## Look for

1. **Dependency direction violations.** A core unit (pure functions and
   data) that imports, requires, or calls a shell unit (IO, state,
   platform, persistence); a shell unit that calls across to another
   shell when only data should cross; a cycle between two units that are
   therefore one unit or have the boundary drawn wrong. Dependencies
   point inward; the core never reaches up.
2. **Pure, shell, and native leakage.** IO, a clock, a global, a native
   call, a persistence write, or state mutation inside a core unit. Or
   pure decision logic (query construction, view-spec computation, plan
   or transaction-data building) buried in the shell where a test
   cannot reach it. Or domain logic inside a native wrapper (the wrapper
   marshals; it does not decide).
3. **Native edge leakage.** A wrapper that reaches into a handle's
   internals from the calling language, or that holds domain logic
   instead of marshalling data in and data out. Handles are opaque; the
   calling language passes them back, never inspects them.
4. **Duplication.** The same nontrivial logic in two units. Cite both
   sites. The high-risk case is a new variant of an existing path: a
   second codec beside the first, a second transport beside the first,
   a windowed path beside the headless one. Check that it shares the
   existing machinery rather than copying it. A duplicated path drifts;
   the two must match and silently will not. Cite the original it
   should have reused.
5. **Oversized units.** Files past the project's size gate (the
   descriptor's QA lane carries the threshold when one is set; absent
   one, roughly 800 lines for a file, 200 for a function). The
   carve-out: a file split along a domain seam is fine; a file growing
   by accretion is a finding.
6. **Mixed concerns.** A function that builds data and applies the
   effect; a native body that allocates, computes, and reports; a
   handler that decides and persists. The core and shell split exists
   to separate these.
7. **Decisions trapped in effect loops.** A semantic choice inlined in
   an event loop or a callback when it could be a returned value the
   caller switches on (description, not instruction). The inverse is
   equally a finding: a wrapper layer introduced only to fake purity or
   enable mocking, when a direct data read in decision code is correct.
8. **Dead structure.** Public functions with one caller; parameters
   every caller passes identically; a record or struct that wraps a map
   without adding behavior.

Do not propose moves that change a public surface or a native edge
contract. That is a maintainer decision; file as `:medium` with the
suggestion marked cross-module.

## Ignore here

Logic correctness (check-correctness). Naming, idiom, comment debt
(check-style). Conformance to the spec or an ADR (check-conformance).
Memory safety and ownership (check-memory). Security (check-security).
Performance cost (check-performance).

## Severity

- `:medium`. A boundary violation, pure, shell, or native leakage, or
  duplication. Each makes a branch untestable or lets two copies drift.
- `:low`. Size pressure or dead structure with no current harm.

## Level

`:factoring`. Factoring fixes land in the second editor wave, after
correctness and before style.

## Boundaries

Owns: where the code lives and how units depend on each other.
Siblings: check-correctness owns whether it is right; check-style owns
how it reads; check-conformance owns whether it matches the spec;
check-memory owns native state lifetime; check-security owns the trust
lines; check-performance owns the cost.

## Return contract

An EDN vector of finding maps (shape in
`skills/shared/references/review-model.md`), one per defect, each
citing both sites when the finding is duplication or a boundary cross.
When the shard has none, return exactly:

```
NO FINDINGS
```
