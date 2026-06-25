---
name: define-requirements
description: Define testable, scoped requirements
user-invocable: false
---

# Define Requirements

Define WHAT must be built as testable, scoped requirements. No tech stack,
architecture, timelines, or estimates.

## Procedure

1. Read `project-meta.md`, `problem-description.md`, and `open-questions.md`.
   If a prerequisite is missing, return `blocked: <which artifact>`.
2. Surface any `[Blocking]` items affecting `product-requirements.md` and
   resolve them first.
3. Clarify ambiguities, at most three questions per turn: contradictions
   between goals and constraints, implicit assumptions, acceptance criteria
   made concrete and testable. Park unclear requirements in
   `open-questions.md` as `- [ ] [Affects: product-requirements.md] <question>
   (Answer: TBD)`.
4. Iterate until scope is crisp and confirmed. Shape requirements around the
   smallest valuable increments.
5. Write `.claude/artifacts/planning/product-requirements.md` with: Metadata,
   Problem Statement, Goals, Non-Goals, Users, Scope, Functional Requirements
   (each with priority and acceptance criteria), Non-Functional Requirements,
   Workflows, Success Criteria, Assumptions. Keep open questions out of the
   PRD; they live in `open-questions.md`.

## Boundaries

Owns the testable requirements. review-risks owns surfacing risk and
assumptions; design-technical owns HOW. Reached by plan-system.

## Return

One line: the artifact written and a terse status, or `blocked: <reason>`.
