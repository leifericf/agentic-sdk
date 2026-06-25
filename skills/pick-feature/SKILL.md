---
name: pick-feature
description: Select and triage the next feature to implement
user-invocable: false
---

# Pick Feature

Select the best next feature to implement from the backlog, and triage the
inbox as part of selection. Optimize for the smallest user-visible value with
low risk and clear acceptance criteria.

## Procedure

1. Read `product-backlog.md`, `product-requirements.md`, `decision-log.md`,
   and `open-questions.md` (the latter three if present). If the backlog is
   missing or empty, return `blocked: no backlog`.
2. Surface and resolve any `[Blocking]` item affecting backlog selection
   before proceeding.
3. Triage the inbox: promote zero to two clearly apt items into `Now / Next`,
   move clearly lower-priority items to `Later`, and leave vague items.
   Rewrite unclear titles into plain user-facing language. Keep `Now / Next`
   short and ordered; `In product (shipped)` stays plain capability names.
4. Ask a short batch of prioritization questions, at most three, tailored to
   the actual backlog items: the outcome to optimize for, sequencing and
   prerequisite constraints (not timelines), and the preferred shape of the
   first increment (thin vertical slice versus foundation-first). Where you
   can derive three to five mutually exclusive options from the backlog,
   present them as choices; always include "write your own" and "can't answer".
5. Present a shortlist of two to five items from `Now / Next`, each with a
   one-line rationale and any risk or dependency note. Prefer the smallest
   user-visible value, the lowest risk, and the clearest acceptance criteria.
6. Confirm the selection with the user. Write the triaged backlog back to
   `product-backlog.md`.

## Boundaries

Owns selecting the next item and triaging the inbox. create-backlog owns the
initial backlog; plan-feature owns decomposing the chosen item. Reached by
plan-system.

## Return

One line: the selected feature and the triage applied, or `blocked: <reason>`.
