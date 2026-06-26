---
name: analyze-root-cause
description: Trace the causal chain from trigger to failure
user-invocable: false
---

# analyze-root-cause

Trace the full causal chain from trigger to failure, identify defense gaps,
and define preventative controls. Anchor on one precise, falsifiable failure
mode.

## Procedure

1. Anchor on one primary failure mode and a minimal, falsifiable problem
   statement; link secondary failures to it.
2. Gather context, at most three questions, preferring binary (validate each
   causal link) and pick-one (select the most plausible chain branch).
3. Write a one- to three-sentence precise problem statement.
4. Document customer impact: what users experienced, duration, blast radius.
5. Trace the causal chain backward from failure to root trigger; number each
   step.
6. Apply Five Whys if the chain has not reached a systemic root.
7. Technical deep dive: components involved, data shapes, the exact breakage,
   and why tests, monitoring, and process did not catch it.
8. Document reproduction: preconditions, steps, expected versus actual.
9. Summarize the fix applied and the follow-up hardening needed.
10. Define preventative controls, each with type (test, monitoring, process,
    architecture), what it prevents, how to verify it, and owner.
11. Assess residual risk and why it is acceptable.
12. Write `.agentic-sdk/artifacts/ops/YYYY-MM-DD_rca_<incident_slug>.md` with:
    Metadata, Problem Statement, Customer Impact, Causal Chain, Five Whys
    (optional), Technical Deep Dive, Reproduction Notes, Fix Summary,
    Preventative Controls, Residual Risk, Open Questions. Slug: lowercase,
    hyphens, descriptive.

## Boundaries

Owns the causal chain and preventative controls. review-incident owns the
blameless incident review; assess-risk owns pre-deployment release risk.
Reached by investigate.

## Return

One line: the artifact written, the root cause, and the top preventative
control, or `blocked: <reason>`.
