---
name: assess-observability
description: Plan logging, metrics, and tracing for a feature
user-invocable: false
---

# Assess Observability

Assess a feature's logging, metrics, and tracing needs. High-level and
declarative (what to observe, not which tool); stack-agnostic; never log
secrets or PII.

## Procedure

1. Read the dispatched inputs: the Gherkin specification, the feature scope,
   and the technical context (systems touched, data flows).
2. Determine applicability. Required when the feature touches money or
   payments, authn or authz or access control, permissions or account state,
   irreversible actions, PII or sensitive data, data-loss or corruption risk,
   integration boundaries, or scheduled and async flows. N/A when it is purely
   visual, a no-behavior-change refactor, docs, or low-risk internal tooling.
3. If Required, define three to six user-visible failure modes and how each
   should degrade. Define structured log events at boundaries (name, level,
   key fields; confirm no secrets or PII). Define at least one signal per
   critical flow (counter, gauge, or histogram) with healthy and broken
   thresholds.
4. If N/A, give a one-line reason.

## Boundaries

Owns observability (logs, metrics, traces). assess-testing owns test tiers;
plan-feature owns folding the result into the plan. Reached by plan-feature's
fan-out.

## Return

One EDN map: `{:applicability :required|:n/a :n/a-reason <str>
:failure-modes [...] :logs [...] :signals [...]}`. For N/A, only
`:applicability` and `:n/a-reason`.
