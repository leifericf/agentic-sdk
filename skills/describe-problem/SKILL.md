---
name: describe-problem
description: Clarify the user problem, constraints, and success criteria
user-invocable: false
---

# Describe Problem

Clarify the real user problem, constraints, and success criteria in user and
business terms. Define WHAT, not HOW.

## Procedure

1. Read `.agentic-sdk/artifacts/project/project-meta.md` and
   `.agentic-sdk/artifacts/decisions/open-questions.md`. If either is missing,
   return `blocked: bootstrap the planning artifacts first`.
2. Collect a single-paragraph problem statement from the dispatch prompt;
   accept rough notes or bullets.
3. Clarify through follow-ups, at most three per turn: who experiences the
   problem, what observable success looks like, what is in and out of scope,
   the constraints (technical, operational, legal, integration, budget), and
   the risks and unknowns. Surface hidden complexity and assumptions;
   challenge vague words ("fast", "secure") into concrete definitions.
4. Iterate until the problem is understood and the summary is confirmed.
   Park unresolved questions in `open-questions.md` under `Open` as
   `- [ ] [Affects: problem-description.md] <question> (Answer: TBD)`; mark
   `[Blocking]` only if you cannot proceed.
5. Write `.agentic-sdk/artifacts/planning/problem-description.md` with these
   sections: Metadata, Summary, Problem, Desired Outcomes, Stakeholders,
   Current Workflows, In Scope, Out of Scope, Constraints, Risks, Unknowns,
   Simplification Opportunities, References.

## Boundaries

Owns the problem in user and business terms. define-requirements owns the
testable requirements; design-technical owns the architecture. Reached by
plan-system.

## Return

One line: the artifact written and a terse status, or `blocked: <reason>`.
