---
name: triage-conformance-diffs
description: Classify differential-probe divergences into real bug, intentional divergence, or harness artifact, and route each to its owner. Invoked by reviewer agents over one module shard of diff output.
user-invocable: false
---

# triage-conformance-diffs

Classify the divergences a differential conformance probe reported.
This is check-conformance applied to *diff output* instead of source:
the finding arrives pre-detected (expected versus actual, byte for
byte); the judgment is what the difference *is* and who owns it. Work
one module shard at a time so each verdict lands next to the code that
would fix it.

## Inputs

The probe's failure verdicts (key, expected from each ground truth,
actual), the corpus tuples behind them, the intentional-divergence
allowlist, the auto-captured regression files, the ADR store, and any
named divergence decisions the project records. Read all of them before
classifying anything; most wrong verdicts come from not knowing a
divergence was already adjudicated.

## The three verdicts

1. **`:real-bug`.** The reference behavior is the contract and the
   implementation misses it. Includes the quiet variants: a wrong
   result type that prints differently, an unsupported case passed
   through silently instead of thrown (a no-fakery violation even when
   the happy path works), off-by-one realization of lazy input.
   Route: file one bug entry per root cause (not per tuple; twelve
   tuples failing on one formatter gap are one bug), mark the tuples
   pending against it, hand the entry to fix-bug with the auto-captured
   reproducer. Rank by least surprise: a silent wrong answer outranks a
   loud error.
2. **`:intentional`.** An ADR or named decision designates the
   divergence. Route: an allowlist entry whose reason cites the
   decision (`ADR-NN` or the decision's name), never a bare "expected".
   If the divergence is deliberate but *no record covers it*, it is not
   intentional yet: invoke record-decision first or downgrade to
   `:real-bug`. The allowlist never absorbs open bugs, print noise, or
   anything you cannot cite.
3. **`:artifact`.** The harness, not the implementation: print-bound
   mismatch between capture and probe, environment leakage (locale,
   platform newline) making ground truth machine-relative, a tuple that
   violated the corpus rules (nondeterminism, ambient effects), two
   ground truths that disagree with each other. Route: fix the harness
   or the tuple, then re-capture; never allowlist an artifact, that
   buries a harness bug in the conformance ledger.

## Dedup before filing

A divergence is new only if no allowlist entry covers its key or var,
no existing regression file reproduces the same root cause, and no open
bug entry names it. Fold repeat findings into the existing entry;
growing one bug's tuple list is signal, a duplicate bug entry is noise.

## Verify before verdict

Reproduce at least one tuple per root cause with the auto-captured
regression file before filing. When bb-style and reference ground
truths disagree with each other, the reference wins and the disagreement
itself is worth a note; when actual output matches *either* ground
truth the probe already passed it, so a failure means it matched
neither: check the harness before blaming the implementation.

## Boundaries

Owns the verdict and the routing of each divergence. Siblings:
check-conformance owns reviewing source for drift the probe cannot see;
extend-conformance-corpus owns adding tuples and their pending-bug
marks; fix-bug owns the fix; record-decision owns making a divergence
intentional. Does not edit implementation code, and never weakens a
tuple to make a diff go away.

## Return

An EDN vector, one map per root cause:
`{:verdict :real-bug|:intentional|:artifact :module "..." :keys [...]
:route "..." :cites "ADR-NN|decision|bug-entry"}`.
When the shard has no divergences, return exactly `NO DIVERGENCES`.
