---
name: assess-rollout
description: Define deployment strategy and verification
user-invocable: false
---

# assess-rollout

Define the rollout strategy and verification for a feature. Declarative and
stack-agnostic; match strategy to risk; verify from the user perspective.

## Procedure

1. Read the dispatched inputs: the Gherkin specification, the feature scope,
   and the technical context (deployment environment, risk level).
2. Determine applicability. Required when the feature deploys to real users,
   changes core flows, or has significant blast radius. N/A when it is purely
   internal or tooling, has no production deployment, or is plumbing with no
   user impact.
3. If Required, choose a strategy (feature flag for high-risk or toggleable
   changes; canary for infrastructure or performance-sensitive work; staged
   for multi-region or multi-environment; all-at-once for low-risk fixes) and
   give the rationale.
4. Define guardrails: error-rate or latency thresholds, specific errors,
   business-metric drops, each with a threshold and an action (pause,
   rollback, investigate).
5. Define a two- to six-step verification smoke path under two minutes that
   exercises the primary flow on real or production-like data. Define one to
   three signals to watch with healthy and broken values.

## Boundaries

Owns the deployment and verification strategy. assess-data owns migrations;
assess-risk owns release-time production risk. Reached by plan-feature's
fan-out.

## Return

One EDN map: `{:applicability :required|:n/a :n/a-reason <str> :strategy ...
:rationale <str> :guardrails [...] :verification [...] :signals [...]}`. For
N/A, only `:applicability` and `:n/a-reason`.
