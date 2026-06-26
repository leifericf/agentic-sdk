---
name: advance-plan
description: Drive a chunk of the plan to completion unattended, phase by phase
disable-model-invocation: true
---

# advance-plan

Role: the campaign driver. Takes a named chunk of an approved plan, has
a planner decompose it into a forward-only DAG of phases, secures one
approval, then walks the DAG dispatching one change-runner per phase,
unattended.

## Prerequisites

An approved feature plan from plan-system (a `planning/tasks/plan-*.md`
file), or a named chunk the maintainer points at. `.agentic-sdk/project.edn`
exists with `:lanes` and `:spine` set.

## Procedure

Read `skills/shared/references/orchestration.md` before driving. Its
laws bind this skill: context is the budget, the work flows forward-only
as a DAG, the stack stays linear on the tip, and agents decide and
record rather than stall.

1. **Check for a resume.** If `.agentic-sdk/runs/<slug>/checkpoint.edn` exists
   for this chunk, resume: read it and the plan at
   `.agentic-sdk/runs/<slug>/plan.edn`, skip landed phases, re-dispatch any
   in-flight phase, continue the open ones. Resume from the checkpoint and
   plan, not from history.
2. **Plan up front, in a sub-agent.** Dispatch one planner agent running
   plan-work. Its prompt is self-contained: the named chunk (or "next
   runnable work") and a pointer to the feature plan. The planner reads
   what is already landed (the commit log, the modules and tests that
   exist), picks the chunk in dependency order, and decomposes it into a
   forward-only DAG of phases and tasks. For each task it names the
   planned commit and the specialist that will execute it (a writer with
   the relevant write-<lang> recipe or write-tests, a reviewer, and so
   on). It tags each phase with a risk tier that sets the model and
   effort: HIGH-RADIUS (a native edge between languages, a query or
   codegen path, a security seam, a cross-module or wire-format change)
   runs at the full tier; MECHANICAL (a test-only phase, a scaffold, a
   single-module pure refactor) runs at a cheaper model and lower
   effort. Each phase carries its own tests, its check-security pass
   where untrusted input is involved, and its verification lanes in its
   definition of done. The planner writes the full plan to the
   gitignored `.agentic-sdk/runs/<slug>/plan.edn` and returns a compact
   summary: the phases, their dependency edges, any ADR conflict it
   found. Hold the summary, never the full plan.
3. **Approve once.** Surface the plan summary to the maintainer: the
   phases, their order, the dependency rationale, any ADR conflict.
   This is the only up-front gate. On approval, write the initial
   checkpoint and a campaign-context note
   (`.agentic-sdk/runs/<slug>/context.md`, gitignored) holding the
   invariants every phase shares: the base-on-tip rule and the base
   commit, the env contract, the commit and review policy. Each
   dispatch points to this note instead of retyping invariants, paying
   their token cost once. Then proceed unattended.
4. **Auto-dispatch the DAG.** Walk the DAG in topological order. For
   each phase whose dependencies have all landed, dispatch one
   change-runner via the Agent tool, at the model and effort the
   phase's risk tier names. Its prompt is self-contained but lean: the
   phase name, a pointer to its entry in `plan.edn` (cited, not pasted),
   and a pointer to the campaign-context note for the shared
   invariants. The change-runner reads its phase from the plan, runs
   implement-change for it end to end in its own context, and returns
   exactly one line and nothing else:

   `SLICE <name>: <n> units landed, <e> escalated, <d> decisions, verify <PASS|FAIL>, <done|blocked>`

   Parse the slice name, unit count, escalation and decision counts,
   verify verdict, and outcome from that line; it is the whole hand-off.
   No per-phase confirmation. Minimize work in progress: a phase lands
   before its dependents start. Independent phases may run in one batch;
   keep concurrency modest and respect the graph. Hold each returned
   line and update the checklist; do not read into the phase. On `blocked`, stop dispatching dependents of
   the blocked phase and record the escalation; phases that do not
   depend on it continue.

   The change-runner's review sub-runners also return a `dry` or
   `continue` signal for the round cap. A `dry` round from a phase's
   review means that phase is clean (no new findings) and the campaign
   advances; do not block on it. `continue` ran another round under the
   cap; the change-runner's own line still says `done` or `blocked`.
5. **Decide, record, checkpoint.** The run does not stall on ambiguity.
   Each change-runner's agents decide and record to unblock; the runner
   returns those decisions and any escalation in its line. Accumulate
   every returned decision (what was chosen, why, the rejected
   alternative) into the gitignored
   `.agentic-sdk/runs/<slug>/decisions.edn`, and phase progress (done,
   in flight, open) into `.agentic-sdk/runs/<slug>/checkpoint.edn`.
   These are the only things this campaign writes to disk: ephemeral,
   gitignored, never committed, never the hand-off medium. A crash or
   token ceiling resumes from the checkpoint and the plan, not the
   history. The durable artifacts are the code, the tests, the
   changelog, and any ADRs the phases settle.
6. **Session-end review.** When the checklist is exhausted (or a block
   halts progress), mark the deliverable. Place one jj bookmark at its
   tip named in plain words for what shipped (`catalog-core`, `diffing-renderer`): no
   slice, phase, or plan identifiers, no numbers. `jj bookmark create
   <name> -r <tip>`. One bookmark per deliverable, at the deliverable
   boundary, never on an interior phase. Then end with one compact
   report: what landed (each phase with its verify verdict), the
   decisions log (every unblocking decision, each with its rationale
   and rejected alternative), the escalations and what blocked them,
   and what remains open in the chunk. Any decision that proves
   architectural is promoted to an ADR via record-decision; any mid-run
   correction the maintainer gives goes to capture-guidance. Then, as
   the campaign's last action, invoke vertical-slice-postmortem through
   the Skill tool on the landed chunk. This is mandatory and self-invoked:
   it root-causes the slice's review findings and its token and
   verification cost into recipe improvements, so the next campaign is
   higher quality, cheaper, and does not repeat the same defect class.
   Run it once. A campaign that finishes clean can delete its
   checkpoint.

## Boundaries

Reads only the one-line returns of the planner and each change-runner,
never their diffs, file bodies, findings dumps, or reasoning. Per-phase
detail lives in those agents' contexts and is thrown away when they
return. Holds the plan summary, the phase checklist, and the returned
lines. The final land (advancing the protected main line over the
stack) waits for the maintainer; an autonomous campaign never advances
main itself. Atoms dispatched: planner (running plan-work), one
change-runner per phase (running implement-change, which dispatches
writer, verifier, and review-round-runner), record-decision for
architectural choices, capture-guidance for mid-run corrections,
vertical-slice-postmortem at the end.

## Return

A session-end report: the phase outcomes with verify verdicts, the
decisions log, the escalations, and what remains open, plus one
plain-word bookmark at the deliverable tip.
