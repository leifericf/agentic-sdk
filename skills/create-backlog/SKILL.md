---
name: create-backlog
description: Create a prioritized backlog from the technical design
user-invocable: false
---

# create-backlog

Translate the technical design into a prioritized, living product backlog of
capabilities. No implementation task breakdown.

## Procedure

1. Read `project-meta.md`, `problem-description.md`, `product-requirements.md`,
   `risk-assumption-review.md`, `technical-design.md`, `decision-log.md`, and
   `open-questions.md` (`ux-design-guide.md` optional). If a required
   prerequisite is missing, return `blocked: <which artifact>`.
2. Surface and resolve `[Blocking]` items affecting `product-backlog.md`.
3. Draft a simple capability hierarchy from the artifacts; iterate with the
   user to confirm priority, sequencing, and scope, at most three questions
   per turn. Check for missing capabilities and validate priority assumptions.
4. If blocked by missing information, add a `[Blocking]` item to
   `open-questions.md` and stop. If new decisions are needed, record them in
   `decision-log.md` first.
5. Write `.agentic-sdk/artifacts/planning/product-backlog.md` as one living document
   with exactly these buckets in order: `Now / Next`, `Later`,
   `Inbox (untriaged)`, `In product (shipped)`. No IDs; priority is order
   within the bucket; each item appears once in one bucket; child items indent
   two spaces under their parent; no prose around the list. `In product
   (shipped)` holds plain capability names only, no dates or versions.

## Boundaries

Owns the prioritized capability backlog. pick-feature owns selecting and
triaging the next item; plan-feature owns decomposing one item into a plan.
Reached by plan-system.

## Return

One line: the artifact written and a terse status, or `blocked: <reason>`.
