---
name: plan-feature
description: Turn a backlog item into slices, a Gherkin spec, and a plan
user-invocable: false
---

# plan-feature

Turn a selected backlog item into a dependency-ordered plan with a Gherkin
acceptance gate and parallel quality-dimension assessments. Two modes: plan
(default) and review (revise an existing plan).

## Procedure

1. Read `product-backlog.md`, `product-requirements.md`, `decision-log.md`,
   and `open-questions.md`. In review mode, read the existing
   `~/.agentic-sdk/<project>/artifacts/planning/tasks/plan-<slug>.md` instead and treat its
   Functional Snapshot and Gherkin as source of truth. Surface and resolve
   any `[Blocking]` item affecting this feature first.
2. Functional elicitation gate. Read the backlog item and relevant
   requirements; fold in resolved open-question answers. If requirements are
   missing or ambiguous, ask in this order, at most two batches of three
   questions: problem and intent; success criteria (observable); user outcomes
   (must do, must never happen, edge cases); user flow (primary, alternate,
   friction); business and domain rules; integrations and their failure
   behavior; prioritization (MVI, what is deferred); constraints (privacy,
   security, compliance, operational); delight and quality bar. Do not proceed
   until you can answer in plain language: the primary user and goal,
   observable success, the primary flow, what must never happen, the key edge
   cases, the business rules, the integrations and their failure behavior, the
   MVI, and what is deferred. If any blocker remains, add a `[Blocking]` open
   question and return `blocked`.
3. Write the Gherkin specification. Treat it as source of truth for
   feature-level acceptance: business language and user-observable outcomes,
   not UI mechanics. Three to seven scenarios for the MVI, deterministic, each
   asserting externally visible state. Include at least one happy path, one
   must-never-happen, one key edge case, and one integration or
   dependency-failure scenario where applicable. Use `Feature:` with a short
   narrative, `Background:` only for truly shared preconditions, `Rule:` to
   group business rules, and `Scenario Outline:` with `Examples:` for
   permutations.
4. Fan out the four planning dimensions: dispatch assess-observability,
   assess-testing, assess-data, and assess-rollout in parallel (module-batch
   fan-out; if your context has no sub-agent tool, run them inline per
   `orchestration.md`). Collect each one-line EDN assessment.
5. Plan chunks and tasks. Decompose into two to six independently testable
   chunks that each deliver user value and reduce risk; plan the next chunk in
   full and keep later chunks lighter. Write tasks commit-per-task, each leaf
   completable in one commit. Capture unrelated later ideas under
   `Inbox (untriaged)` in `product-backlog.md`; do not expand scope unless the
   user changes the definition of done. Keep the plan a forward-only DAG:
   security and verification ride with each chunk, not bolted on at the end.
6. Write the plan to `~/.agentic-sdk/<project>/artifacts/planning/tasks/plan-<feature_slug>.md`,
   filling the section skeleton in `plan-template.md`. Fold the four dimension
   assessments into their sections (Observability, Testing Strategy, Data and
   Migrations, Rollout and Verify); surface any `N/A` with its reason. Follow
   the project descriptor's VCS and commit form, not any retired commit
   mechanics in the skeleton.
7. Review mode. Over an existing plan, surface the gaps that cause rework:
   success criteria that are not observable, flow ambiguity, missing
   must-never-happen outcomes, unstated edge cases, ambiguous rules,
   unspecified integration failure behavior, and an unclear split between the
   MVI and the deferred scope. Ask the smallest set of clarifying questions,
   at most two batches of three, in the elicitation order above; prefer plain
   language and choice questions. Revise the Functional Snapshot, Gherkin,
   chunks, and tasks, and write the plan back.

## Boundaries

Owns decomposing one backlog item into a plan plus its quality-dimension
assessment, and reviewing that plan. pick-feature owns selection; the four
assess-* own their single dimension; implement-change and advance-plan own
execution. Reached by plan-system.

## Return

One line: the plan written or revised and a terse status, or
`blocked: <reason>`.
