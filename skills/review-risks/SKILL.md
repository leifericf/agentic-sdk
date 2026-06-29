---
name: review-risks
description: Surface risky assumptions and missing acceptance criteria
user-invocable: false
---

# review-risks

Surface ambiguity, contradictions, risky assumptions, and missing acceptance
criteria. Translate risks into testable questions and mitigations.

## Procedure

1. Read `project-meta.md`, `problem-description.md`, `product-requirements.md`,
   and `open-questions.md`. If a prerequisite is missing, return
   `blocked: <which artifact>`.
2. Surface any `[Blocking]` items affecting `risk-assumption-review.md` and
   resolve them first.
3. Pull likely risks and assumptions from the artifacts rather than ask the
   user to enumerate them. Look for missing requirements, contradictions,
   scope-creep signals, over-engineering traps, dangerous assumptions, and
   organizational risks.
4. Confirm with targeted questions, at most three per turn; prefer binary and
   pick-one over open-ended. Continue until uncertainty is acceptably low.
5. Write `~/.agentic-sdk/<project>/artifacts/planning/risk-assumption-review.md` with:
   Metadata, Confirmed Truths, Key Risks (category, likelihood, impact,
   mitigation, owner), Dangerous Assumptions, Scope Creep Watchlist,
   Over-Engineering Traps, Recommended Simplifications. Record new open
   questions in `open-questions.md`.

## Boundaries

Owns risk and assumption surfacing across the planning artifacts.
define-requirements owns the requirements; design-technical owns technical
tradeoffs. Reached by plan-system.

## Return

One line: the artifact written and a terse status, or `blocked: <reason>`.
