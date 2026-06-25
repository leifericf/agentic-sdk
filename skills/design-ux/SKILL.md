---
name: design-ux
description: Establish UX guardrails and interaction patterns
user-invocable: false
---

# Design UX

Establish UX guardrails, visual direction, and interaction patterns across
web, mobile, desktop, TUI, and CLI surfaces. Reduce interface entropy with
reusable rules. UI only.

## Procedure

1. Read `project-meta.md`, `problem-description.md`, `product-requirements.md`,
   `risk-assumption-review.md`, `decision-log.md`, and `open-questions.md`. If
   a prerequisite is missing, return `blocked: <which artifact>`.
2. Run the applicability gate. If the project has no user-facing surface (pure
   library, backend-only service, batch job, data pipeline), do not produce the
   artifact. Append a `decision-log.md` row: decision "Skip UX design guide",
   why "no user-facing surfaces in scope", tradeoff "revisit if a UI is added".
   Return `skipped: no user-facing surface`.
3. Surface and resolve `[Blocking]` items affecting `ux-design-guide.md`.
4. Collect three to five visual references (URLs, screenshots, CLI or TUI
   tools, or a textual direction). If none, pick a direction: Minimal,
   Editorial, Playful, Enterprise, Retro, or Luxury.
5. Clarify, at most three per turn: primary interface surfaces, brand tone,
   density, target users and context, accessibility expectations, input
   modalities, offline and latency constraints. Do not assume UI toolkits
   unless already decided.
6. Record new decisions in `decision-log.md`.
7. Write `.agentic-sdk/artifacts/planning/ux-design-guide.md` with: Metadata,
   Visual References, Design Principles, Interface Surfaces, Accessibility,
   Typography, Color System, Layout Rules, Components, CLI/TUI Conventions
   (if applicable), Interaction Patterns, Content Style, UI Definition of Done.

## Boundaries

Owns interaction and visual guardrails. design-technical owns system
architecture and the stack; only runs when a user-facing surface exists.
Reached by plan-system.

## Return

One line: the artifact written (or `skipped: no user-facing surface`) and a
terse status, or `blocked: <reason>`.
