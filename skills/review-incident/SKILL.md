---
name: review-incident
description: Run a blameless incident review with action items
user-invocable: false
---

# review-incident

Run a blameless incident review focused on learning and preventing recurrence.
Factual, empathetic, thorough; never blame-oriented. Start with impact,
detection, and resolution; fill the timeline as facts arrive.

## Procedure

1. Gather context, at most three questions, preferring binary and pick-one;
   never use blame-oriented language.
2. Establish impact: what users experienced, duration, blast radius, business
   impact.
3. Understand detection and any detection gap.
4. Build the timeline in chronological order (UTC recommended).
5. Document the response: what was done, what worked, what did not.
6. Review internal and external communication.
7. Identify contributing factors across product, technical, data, operational,
   and org or process dimensions.
8. Define action items, each with type (prevent, detect, mitigate), owner, and
   due date.
9. Plan follow-up verification: how to confirm the fixes work.
10. Write `.agentic-sdk/artifacts/ops/YYYY-MM-DD_incident_<incident_slug>.md` with:
    Metadata, Summary, Impact, Detection, Timeline, Response, Communication,
    Contributing Factors (blameless), Action Items, Follow-Up Verification,
    Open Questions. Slug: lowercase, hyphens, descriptive.

## Boundaries

Owns the blameless incident review and its action items. analyze-root-cause
owns the technical causal chain; triage-logs owns live signal triage. Reached
by investigate.

## Return

One line: the artifact written, the severity, and the open action-item count,
or `blocked: <reason>`.
