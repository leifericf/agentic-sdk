---
name: plan-system
description: Turn a problem or idea into an approved backlog and feature plan
disable-model-invocation: true
---

# plan-system

Role: the upstream planning cycle. Takes a problem or idea to an approved
backlog and a feature plan, ready to hand to advance-plan or
implement-change.

## Prerequisites

`.agentic-sdk/project.edn` exists (run bootstrap-project first).
`.agentic-sdk/artifacts/` is scaffolded: project-meta, the decisions log, and
open-questions.

## Procedure

1. **Describe the problem.** Dispatch describe-problem. Elicit the
   problem, the stakeholders, the constraints, and the success criteria.
   It writes `planning/problem-description.md` and parks unresolved items
   in `open-questions.md`. Hold its one-line return.
2. **Define requirements.** Dispatch define-requirements against the
   problem statement. It writes `planning/product-requirements.md` with
   testable functional and non-functional requirements. Resolve any
   blocking open question before continuing.
3. **Review risks.** Dispatch review-risks against the requirements and
   the problem statement. It surfaces contradictions, risky assumptions,
   and missing acceptance criteria, and writes
   `planning/risk-assumption-review.md`.
4. **Design the UX, when the descriptor sets `:ui? true`.** Dispatch
   design-ux. It writes `planning/ux-design-guide.md`. Skip this step
   when the project has no UI surface; the rest of the cycle does not
   depend on it.
5. **Design the technical solution.** Dispatch design-technical against
   the requirements, the risk review, and the UX guide (when present).
   It writes `planning/technical-design.md` against the project's
   architecture pattern (Functional Core / Imperative Shell by default)
   and the active languages, and records any real choice via
   record-decision.
6. **Create the backlog.** Dispatch create-backlog from the requirements,
   the risk review, the UX guide (when present), and the technical
   design. It writes `planning/product-backlog.md` in the four-bucket
   form: Now/Next, Later, Inbox, In product.
7. **Pick and plan the first feature.** Dispatch pick-feature against the
   backlog, then plan-feature on the picked item. plan-feature runs the
   functional elicitation gate (problem, success, flows,
   must-never-happen, edge cases, the minimal viable increment), writes
   the Gherkin acceptance spec, fans out the assess-* concerns
   (assess-observability, assess-testing, assess-data, assess-rollout)
   in parallel, and writes the feature plan to
   `planning/tasks/plan-<feature>.md`. The Gherkin gate is folded into
   plan-feature; plan-system does not run it as a separate step.
8. **Present the gate.** Show the maintainer: the backlog, the picked
   feature, and the feature plan summary. The plan plus the backlog is
   the one approval gate. On approval, hand the feature plan to
   advance-plan (for a campaign of several slices) or implement-change
   (for one slice).

## Boundaries

Reads only the one-line returns of the planning recipes it dispatches,
never their draft prose or their reasoning. Each planning recipe owns
its artifact; this skill stitches their summaries and presents the
gate. It writes no code and runs no lanes. It does not start a
campaign; it stops at the approved plan. Atoms dispatched:
describe-problem, define-requirements, review-risks, design-ux
(conditional on `:ui?`), design-technical, create-backlog, pick-feature,
plan-feature (which itself runs the Gherkin gate, fans out the four
assess-* concerns, and calls record-decision for real choices).

## Returns

One line per planning recipe plus the approved backlog and feature plan
paths, ready to hand to advance-plan or implement-change.
