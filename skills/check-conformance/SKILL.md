---
name: check-conformance
description: Review dimension for divergence from the spec, the ADRs, and the language standard. Invoked by reviewer agents over one module shard.
user-invocable: false
---

# check-conformance

Role: review the assigned shard for divergence from the canonical sources
of truth.

Failure model: the code works on the happy path but behaves differently
from what the spec promises, what an ADR decided, or what the language
standard guarantees, and the divergence is undocumented.

Three layers apply, in this order.

1. **The spec** (the design docs under `docs/`, the dossier the
   descriptor names, the public API docs) is the design contract.
   Behavior the code exhibits that the spec specifies differently is a
   finding; behavior the spec does not address is not (it may be a
   correctness or factoring finding under another dimension, but not a
   conformance finding).
2. **The ADRs** (the `:adr :store` directory) are dated decisions. Code
   that violates an ADR is a `:high` finding citing `ADR-NN`. Code that
   follows a different path than an ADR because the ADR was superseded
   must cite the supersedence at the call site; an unstated supersedence
   is a finding.
3. **The language standard** is the spec for the language surface. The
   pure core must match the host language's observable behavior for nil
   and null handling, empty collections, laziness and strictness
   boundaries, arity and signature errors, numeric tower promotion,
   ordering guarantees on sorted versus hash collections, integer
   overflow semantics, and error-propagation shapes (Result, error
   union, exception). The native wrappers follow the edge contract in
   `skills/shared/references/architecture.md` exactly; a mismatch
   between the calling-language signature and the contract is a finding.

## Look for

1. **Semantic drift.** Edges where the implementation's behavior would
   differ from the spec or the language standard: null where a
   docstring promises a value, empty-collection return where the spec
   specifies an error, laziness where the spec specifies eagerness,
   overflow where the spec specifies wrapping.
2. **Undocumented deviations.** Behavior that differs on purpose from
   the spec but has no deviation comment at the implementation site.
   The idiom is a comment naming what the spec says and what the code
   does instead.
3. **Edge contract drift.** A native call whose signature disagrees with
   the contract on argument types, return types, or handle ownership.
   The contract is normative; the handle catalog is the spec.
4. **Error-shape mismatches.** An exception thrown where the spec
   specifies a returned error value (or vice versa); an error code not
   in the spec's diagnostic catalog; a diagnostic that hides the
   failing function or signature.
5. **Docstring and comment lies.** Docstrings that promise behavior the
   implementation does not deliver, or that describe an older shape of
   the API.

Classify every gap with the no-workarounds law: a real spec gap is a
finding; an upstream platform difference (host behavior, OS variance)
needs a site comment, not a shim; an infrastructure issue (test
harness, runner) is a finding against the harness. Never suggest
special-casing a caller or weakening a test to hide a gap.

## Ignore here

Logic correctness apart from the spec (check-correctness). Module
boundaries (check-factoring). Style and naming (check-style). Security
(check-security). Performance (check-performance). Portability
(check-portability).

## Severity

- `:high`. Silent wrong answers; a violation of an ADR or the edge
  contract.
- `:medium`. Wrong error shapes; an undocumented deviation that could
  mislead a caller.
- `:low`. Docstring gaps; stale comments.

## Level

`:correctness` for behavior and contract drift; `:style` for docstring
and comment gaps. Most conformance findings land in the first editor
wave.

## Boundaries

Owns: whether the code matches the spec, the ADRs, and the language
standard. Siblings: check-correctness owns behavior that is simply
wrong regardless of the spec; check-factoring owns where it lives;
check-style owns how it reads; `record-decision` owns writing the ADRs
this dimension cites.

## Return

An EDN vector of finding maps (shape in
`skills/shared/references/review-model.md`), one per defect, each
citing the spec section or `ADR-NN` it diverges from. When the shard
has none, return exactly:

```
NO FINDINGS
```
