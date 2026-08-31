---
name: extend-conformance-corpus
description: Grow a differential conformance corpus with per-target edge forms, free palette tier first, judgment tier for the reasoning-gated remainder. Invoked by writer agents when a conformance generation unit is dispatched.
user-invocable: false
---

# extend-conformance-corpus

Grow the corpus a differential probe runs against a reference
implementation. This is a write-tests specialization: every tuple is a
test whose expected output is *captured from the reference*, never
hand-written, so every assertion can fail and none can lie. The probe,
fixtures, and capture tooling already exist; this recipe only decides
what new forms enter the corpus. It never edits the probe.

## Pick targets first

Rank untargeted surface, not favorites:

1. Public vars the project implements that have **zero corpus tuples**
   (diff the census or surface inventory against the corpus's var set).
2. Vars implicated by **open bug reports** or recent divergence fixes.
3. Hot vars whose misbehavior a user meets on day one.

A generation unit takes a small batch (five to ten vars), not the whole
backlog: ground truth capture and triage stay reviewable per batch.

## Two tiers, cheap one first

- **Palette tier (free).** Mechanical application of the var across the
  fixed edge-value palette the capture tool defines (empty collections,
  nil, zero and negative, extreme integers, exact and inexact numbers,
  chars, symbols, infinite sequences). Declare it; the tool expands it.
  Combinations that throw in the reference are recorded as non-ok
  ground truth and filtered, so a throwing cell costs nothing but a
  corpus entry. Spend no judgment here.
- **Judgment tier.** Hand-reasoned forms for what the palette cannot
  reach: laziness and chunk-realization observability (count the calls
  with a side-effect probe), numeric-tower promotion chains,
  tie-breaking and stability, init-element semantics on empty input,
  early-termination protocols, pad and step extremes, grammar corners
  of string formatters, interactions between two vars. Reason about the
  var's contract and write the forms that would expose a wrong
  implementation strategy, not merely a wrong answer.

## What never enters the corpus

- **Nondeterminism.** No randomness, wall clocks, object identity
  hashes, iteration order beyond what the reference guarantees.
- **Ambient effects.** No filesystem, network, environment reads; a
  side-effect probe is fine only when self-contained (a local atom).
- **Forms that throw in the reference.** The pipeline diffs printed
  values; a reference throw is filtered, so an error-shape probe here
  is a wasted entry. Error conformance is its own probe family.
- **Host-type name probes.** Where the project's type names are a
  designed divergence or a frozen compat surface, `(class x)`-style
  probes only generate noise; probe observable behavior instead.
- **Unbounded output.** Wrap infinite structures in a bounded take;
  respect the print bounds the harness binds.

## Ground truth and pending bugs

Run the capture tool after authoring; it records the reference output
per tuple. Read its non-ok report: a rejected form is a form you
misjudged, fix or drop it before it lands. When a new tuple exposes a
divergence, do not delete it, weaken it, or allowlist it: mark it with
the corpus's pending-bug convention pointing at the bug entry you file,
so the probe skips-and-counts it until the fix lands and then bites
forever after. The allowlist is reserved for divergences an ADR or
named decision designates intentional.

## Teeth rule

Before committing a batch, prove the batch can fail: the probe run must
show every new non-pending tuple passing against the current binary and
the pending count going up by exactly the divergences you filed. A
batch that "passes" because its tuples were filtered out of the run is
a silent no-op; check the corpus count in the probe's summary line.

## Boundaries

Owns which forms enter the corpus and their pending-bug marking.
Siblings: write-tests owns ordinary suite tests; triage-conformance-diffs
owns classifying the divergences the new tuples surface; fix-bug owns
fixing them; record-decision owns declaring one intentional. Does not
edit the differ, the capture tool, or ground truth by hand.

## Return

One line: vars covered, tuples added (palette / judgment split),
divergences surfaced, pending-bug entries filed.
