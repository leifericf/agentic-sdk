---
name: investigate
description: Triage an incident to root cause and a follow-up plan
disable-model-invocation: true
---

# investigate

Role: the ops entry point. Takes an incident (alerts, logs, a failing
behavior, a report) to a root-cause analysis, a blameless review, and a
follow-up plan ready to enter the backlog.

## Prerequisites

At least one of: a log excerpt or alert payload, a time window and
environment, a reproduction, or an incident summary.
`.agentic-sdk/project.edn` and `.agentic-sdk/artifacts/ops/` exist (run
bootstrap-project first).

## Procedure

1. **Triage the signals.** Dispatch triage-logs with the smallest
   useful packet: one alert or error, the timeframe, the environment.
   It separates symptoms from operator signals, assesses impact,
   checks recent changes, forms ranked hypotheses, and proposes
   reversible mitigations. It writes `ops/<date>_triage_<slug>.md`.
   Hold its one-line return.
2. **Trace the root cause.** Dispatch analyze-root-cause against the
   triage artifact. It anchors on one precise failure mode, traces the
   causal chain back from failure to trigger, runs the technical deep
   dive (why tests and monitoring missed it), documents reproduction,
   and defines preventative controls (each with type, what it prevents,
   how to verify, owner). It writes `ops/<date>_rca_<slug>.md`. Hold
   its one-line return.
3. **Review the incident, blameless.** Dispatch review-incident against
   the triage and the RCA. It establishes impact and detection, builds
   the timeline, documents the response, identifies contributing factors
   across product, technical, data, operational, and process
   dimensions, and defines action items (each with type: prevent,
   detect, or mitigate; owner; due date). It writes
   `ops/<date>_incident_<slug>.md`. Hold its one-line return.
4. **Draft the follow-up plan.** From the three returns, compose the
   follow-up plan: the immediate fix (one fix-bug or implement-change
   item), the hardening tasks (the preventative controls from the RCA),
   the backlog items (the review's product or process action items),
   and the verification plan (the test or drill that proves the control
   works). Promote any architectural choice the investigation settles
   to an ADR via record-decision. Resolve open questions in
   `open-questions.md`.
5. **Present the gate.** Show the maintainer the triage, RCA, and
   incident review paths plus the follow-up plan. The follow-up plan is
   the one approval gate. On approval, fix items hand to fix-bug (a
   single bug) or implement-change (a slice); backlog items enter the
   backlog via the normal planning cycle.

## Boundaries

Reads only the one-line returns of triage-logs, analyze-root-cause, and
review-incident, never their full artifacts or reasoning. Each recipe
owns its artifact; this skill stitches their summaries and composes the
follow-up plan. It runs no lanes and writes no code; the fixes it
identifies hand off to fix-bug or implement-change. It stops at the
approved follow-up plan; it does not start the fix. Atoms dispatched:
triage-logs, analyze-root-cause, review-incident, record-decision for
architectural choices.

## Return

The three artifact paths (triage, RCA, incident review), a follow-up
plan of fix and hardening items, and the backlog entries, ready to hand
to fix-bug or implement-change.
