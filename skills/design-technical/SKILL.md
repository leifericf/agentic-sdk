---
name: design-technical
description: Design architecture, components, and data flows
user-invocable: false
---

# Design Technical

Design a simple, evolvable system with clear boundaries and data flows. Prefer
operational simplicity; make tradeoffs explicit and recorded. Express the
design in Functional Core / Imperative Shell terms for the project's active
languages (C, Zig, Clojure, Elixir).

## Procedure

1. Read `project-meta.md`, `problem-description.md`, `product-requirements.md`,
   `risk-assumption-review.md`, `decision-log.md`, and `open-questions.md`.
   The `ux-design-guide.md` is optional; proceed without it when the UX step
   was skipped. If a required prerequisite is missing, return
   `blocked: <which artifact>`.
2. Surface and resolve `[Blocking]` items affecting `technical-design.md`.
3. Ask the smallest set of clarifications, at most three per turn: system
   boundaries and key flows, data durability and retention, external
   integrations and their failure behavior, operational posture, team
   constraints, and repo conventions.
4. Set project-wide defaults (not per-feature): observability (logging,
   metrics, tracing) and testing posture (unit, integration, e2e). These are
   starting points refined per feature.
5. Record decisions in `decision-log.md` with date, decision, rationale,
   tradeoff.
6. Write `.claude/artifacts/planning/technical-design.md` with: Metadata,
   System Shape, Domain Boundaries, Components, Key Flows, Data Model,
   Integrity Strategy, Audit and Compliance, Integrations, Technology Stack
   (backend, frontend if applicable, data, infrastructure, observability,
   testing), Repository and Delivery Conventions (structure, command surface,
   CI outline, environment model), Evolution Strategy, Known Tradeoffs. Split
   components and modules along the FC/IS line.

## Boundaries

Owns system architecture, components, data flows, and the stack.
create-backlog owns the work breakdown; design-ux owns the interface. Reached
by plan-system.

## Return

One line: the artifact written and a terse status, or `blocked: <reason>`.
