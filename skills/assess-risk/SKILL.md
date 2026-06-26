---
name: assess-risk
description: Assess pre-deployment risk with rollback plans
user-invocable: false
---

# assess-risk

Pre-deployment risk assessment for a release or change. Ship's release-risk
recipe, not a planning dimension, despite the name.

## Procedure

1. Read the dispatched inputs: a PR or diff summary, release notes or change
   description, feature-flag or config change, and prior incidents or SLOs.
2. Gather context, at most three questions, preferring pick-one (rollout
   strategy, blast radius) and binary (confirm non-negotiables).
3. Summarize the change: what changes, what systems are touched, any
   data-shape changes.
4. Assess blast radius: who and what can be affected, worst-case impact.
5. Map external and internal dependencies and their expected failure behavior.
6. Design the rollout plan: strategy (all-at-once, staged, canary, feature
   flag), steps, guardrails.
7. Design the monitoring plan: key signals, thresholds, where to watch.
8. Design the rollback plan: triggers, steps, expected recovery time.
9. Build the risk register: each risk with likelihood, impact, mitigation,
   detection method, owner.
10. Define go/no-go criteria and render the decision: go, go with conditions,
    or no-go pending.
11. Write `.agentic-sdk/artifacts/ops/YYYY-MM-DD_risk_<change_slug>.md` with:
    Metadata, Change Summary, Blast Radius, Dependencies, Rollout Plan,
    Monitoring Plan, Rollback Plan, Risk Register, Go/No-Go Criteria, Open
    Questions, Decision. Slug: lowercase, hyphens, descriptive.

## Boundaries

Owns pre-deployment production risk for a release. analyze-root-cause owns
incident root cause; assess-rollout owns feature rollout planning during
feature work. Reached by ship.

## Return

One line: the artifact written and the go/no-go decision, or
`blocked: <reason>`.
