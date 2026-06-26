---
name: audit-code
description: Review and fix any scope, round after round until a dry round
disable-model-invocation: true
---

# audit-code

Role: the audit loop. Reviews and fixes existing code at any scope,
running review rounds until a round finds nothing new, then offers the
stack to land.

## Prerequisites

`.agentic-sdk/project.edn` exists with `:lanes`, `:dimensions-active`,
and `:spine`. At least one module has landed; there is code to audit.

## Procedure

The per-round procedure is run-review-round; this skill is the loop
around it. The topology and conflict rules are in
`skills/shared/references/worktree-model.md`.

1. **Initialize.** Start the audit change as the next commit on the
   current tip, never on the main bookmark: `jj new`, then `jj describe
   -m "Refactor: Audit <scope>"`. Hold the scope (a module, a list of
   modules, or a theme) in context; the rest of the run cites it. A
   long audit may keep a minimal, gitignored resume checkpoint under
   `.agentic-sdk/runs/<slug>/` recording what is done (rounds
   completed, findings still open); never committed, never the
   hand-off medium.
2. **Shard the scope** by module ownership: one shard is one module
   directory plus its tests. For a theme scope, grep the theme to a
   file list first, then group by module. Dispatch gather-module-context
   once per shard and reuse the briefs across all dimensions in the
   round.
3. **Rounds while findings warrant.** Dispatch review-round-runner with
   the audit change id, the shards, and the round number. It runs
   run-review-round: fans out the active check-* dimensions over each
   shard, runs the spine triage task to dedupe and order findings,
   dispatches editor waves by level via apply-findings, and re-verifies.
   It returns one summary line; its escalations and out-of-scope items
   ride back in that line and you hold them. A full audit is not bound
   by the per-phase two-round cap implement-change uses: finding
   everything is the point. Keep going while a round still surfaces
   high- or medium-severity findings. A round that finds only
   low-severity or style findings is the last one: record those and
   stop rather than spend another full round. Cap the loop at a round
   budget stated up front (default four) to bound cost; say so if hit.
   Resume after a crash per the worktree-model resume law.
4. **Report.** The audit change is already a linear stack on the tip;
   nothing to restack. Report from context: the rounds table (the
   one-line summaries), what was fixed by level, the escalations the
   rounds returned, and the merged changelog set (the editors'
   `CHANGELOG:` lines from each round summary). Offer the stack to
   land; advancing main is the maintainer's call, never autonomous.

## Boundaries

Reads only the one-line returns of gather-module-context and each
review-round-runner dispatch, never their findings dumps, diffs, or
reasoning. The per-round detail lives in the round-runner's context,
thrown away when it returns. Holds the scope, the shard briefs, and the
round summary lines. An audit fixes what reviews find inside the scope;
out-of-scope findings ride back in the round summaries, reported not
chased, input to a future audit. The final land waits for the
maintainer. Atoms dispatched: gather-module-context (once per shard),
review-round-runner (run-review-round), write-commit and write-changelog
at the land.

## Return

A rounds table (one line per round), the fix counts by level, the
escalations, and the stack head ready for the maintainer to land.
