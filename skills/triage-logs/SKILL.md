---
name: triage-logs
description: Turn noisy signals into structured analysis
user-invocable: false
---

# Triage Logs

Triage production signals into structured, actionable analysis. Methodical,
hypothesis-driven, biased toward reversible mitigations. Start with the
smallest useful packet, not a full data dump.

## Procedure

1. Gather context, at most three questions, preferring binary and pick-one to
   narrow scope: a log excerpt or alert payload, the time window and
   environment, and any recent deploys, flag flips, or config changes.
2. Separate user-visible symptoms from operator-visible signals.
3. Assess impact: user impact, business impact, blast radius.
4. Catalog signals by type: logs, metrics, traces.
5. Check recent changes in the relevant window: deploys, config, feature
   flags.
6. Form testable hypotheses ranked by plausibility.
7. For each hypothesis, define a quick check with expected and actual
   outcomes.
8. Propose mitigations, reversible first, each with its risk and rollback.
9. Prioritize concrete next actions.
10. Write `.claude/artifacts/ops/YYYY-MM-DD_triage_<issue_slug>.md` with:
    Metadata, Summary, Symptoms, Impact Assessment, Observed Signals, Recent
    Changes, Hypotheses, Experiments, Findings, Mitigations (reversible first),
    Next Actions, Open Questions, Data Request Packet. Slug: lowercase,
    hyphens, descriptive.

## Boundaries

Owns signal triage and hypothesis ranking. review-incident owns the blameless
incident review; analyze-root-cause owns the causal chain. Reached by
investigate.

## Return

One line: the artifact written, the top hypothesis, and current impact, or
`blocked: <reason>`.
