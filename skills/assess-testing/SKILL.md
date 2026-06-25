---
name: assess-testing
description: Define the testing strategy for a feature
user-invocable: false
---

# assess-testing

Define the testing strategy (Tier 0/1/2) for a feature. Declarative (what to
test, not which framework); fast feedback first; right-sized, not maximal.

## Procedure

1. Read the dispatched inputs: the Gherkin specification, the feature scope,
   and the technical context (architecture, integrations, data flows).
2. Tier 0 (required for all user-visible changes): unit tests for core logic,
   deterministic fakes or mocks for dependencies, contract tests at API
   boundaries; fast and isolated, seconds.
3. Tier 1 (when applicable): required when the feature touches persistence,
   migrations, background jobs, or integration boundaries; containerized or
   local deterministic integration tests with real or test-container
   dependencies; minutes.
4. Tier 2 (only when needed): required only when correctness depends on
   third-party behavior you cannot simulate; sandbox or external-dependency
   tests; allow retry and quarantine; on-demand before major releases.
5. Define an E2E strategy only when the product has user-visible flows and a
   journey suite exists or is being introduced; focus on critical journeys.
6. Set testing targets: local and CI loop under about five minutes; Tier 0
   every commit; Tier 1 when integration-affecting chunks land; Tier 2
   on-demand before releases.

## Boundaries

Owns the test-tier strategy. assess-observability owns signals; plan-feature
owns folding the result into the plan. Reached by plan-feature's fan-out.

## Return

One EDN map: `{:tier-0 {...} :tier-1 {:required ... :reason ...} :tier-2 {...}
:e2e <str|nil> :goals {...}}`.
