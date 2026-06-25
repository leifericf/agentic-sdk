# Orchestration model

How an entry-point skill (`implement-change`, `audit-code`,
`advance-plan`) fans work across sub-agents and keeps its own context
lean. The project router points here; this file holds the detail. The
companion `worktree-model.md` covers the change-graph topology and
conflict law. `review-model.md` covers the review-round mechanics, the
level discipline, and the finding shape.

## Context is the budget

An agent cannot compact its own context. Every line an orchestrator
reads stays until the run ends, so a bloated orchestrator runs out of
room and stops. The discipline is absolute: an orchestrator reads
only the one-line returns of the sub-agents it dispatches, never a
diff, a file body, a findings dump, or a sub-agent's reasoning. The
heavy reading and writing happens in sub-agent contexts that are
thrown away the moment they return their line. This is what lets a
run go long and unattended without exhausting the token budget or the
context window. Push the work down; keep only summaries up.

## Default to lean

The fleet is capacity, not ceremony. Most work is one maintainer plus
a `writer` and a `verifier`; the reviewer fan-out and editor waves
earn their cost only on a large change or a full audit. Prefer the
smallest dispatch that does the job. The unit of work is a feature
slice, not a language layer: a change crossing a language boundary
(the wrapper on one side, the native body on the other, its
round-trip test, and its resource-leak assertion) is one unit owned
end to end by one writer. Slice by feature, never by language.

The lean default has teeth at the review step too. A per-phase change
runs ONE review round by default. A second round is earned, not
automatic, when EITHER: a high- or medium-severity correctness or
security finding remains OPEN after the round's editor waves; or the
round fixed such a finding by a non-trivial in-round change to
untrusted-input handling, a security boundary, or a compiler or
codegen path, because that fix is itself unreviewed and the
highest-risk seams do not get one unreviewed pass. A finding merely
found and cleanly fixed in-round, on a low-risk seam, does NOT by
itself earn a second round. (The phase touching three or more
modules, a language boundary, or untrusted input still earns one.)
Two rounds is the hard cap for per-phase change work. A round that
would raise only low-severity or style findings does not justify
another round; record those as forward tasks in the decisions log and
pick them up later. (A full audit under `audit-code` is the
exception: it keeps running while a round still finds high- or
medium-severity findings, since finding everything is the point. See
that skill, and `review-model.md` for the round cap.)

## Forward-only: a DAG, not a loop

Work flows one direction, like a build pipeline. The plan is a
dependency DAG of phases and tasks; execution walks it in topological
order. A phase is completed and landed before any phase that depends
on it starts: minimize work in progress, and never backtrack into
landed work to make later work fit. Security and verification shift
left: each phase carries its own tests, its security pass where
untrusted input is involved, and its verification lanes as part of
its definition of done, not as a gate bolted on at the end. If a
later phase reveals that an earlier decision was wrong, that is a new
forward task and a decisions-log entry, not a rewind.

## Hand-off is return values, not files

- **Self-contained dispatch prompts.** Every sub-agent receives its
  full spec in the dispatch: the skill to apply, the unit's spec, the
  module brief from `gather-module-context`, and where its work sits
  in the change graph. A sub-agent never reads the orchestrator's
  conversation history: the prompt is the hand-off in, its return is
  the hand-off out. But campaign-stable invariants that EVERY phase
  shares (the base-on-tip rule, the env contract, the commit and
  review policy) are written ONCE to the run's checkpoint or a
  campaign-context note the sub-agent reads, not retyped verbatim in
  every per-phase prompt; repeating them per dispatch is token waste
  the campaign pays N times. The per-dispatch prompt carries only what
  is specific to this unit, plus a pointer to the shared invariants.
- **Returns are the medium.** A sub-agent passes its result back as
  its final message (the findings, the changelog line, the pass/fail
  verdict, the round summary) and the orchestrator holds it in
  context. Nothing is written to disk to be read back; agents pass
  work to one another directly. Keep returns compact and structured
  (the format lives in each agent file) so the orchestrator stays at
  O(rounds), not O(findings + diffs + chat). Return ONLY the
  contracted shape: when the dispatch asks for one line, return one
  line and nothing else. Narration the caller did not ask for is
  tokens it must hold for the rest of the run; a return longer than
  its contract is a cost defect, treated like any other.
- **Module-batch fan-out.** Dispatch in batches of one module's worth
  of work, in parallel within the batch; wait for the batch; collect
  the returns; then the next batch. Keep concurrency modest (around
  five to seven sub-agents per batch); split a larger batch
  sequentially.
- **Level-ordered editor waves.** Within a review round, all
  correctness findings land before any factoring work; factoring
  before style. Never mix levels in one editor batch: a factoring fix
  in the same wave as a correctness fix obscures the correctness diff.
  The level taxonomy and the reason the order is fixed live in
  `review-model.md`.
- **Gather context once per shard.** A reviewer batch reusing one
  module brief across N dimension dispatches costs N times less
  context than each reviewer rediscovering the module.
- **Retries within budget, then escalate.** A verifier failure feeds
  back to an editor and re-verifies, up to twice; the third failure
  escalates and the round continues. An editor failure on a finding
  retries once; the second records the escalation and the round
  continues. Escalations ride back in the round summary; the
  maintainer sees them at the end, not mid-round.

## Runtime adaptation: fan-out vs inline

The fan-out assumes a sub-agent can itself dispatch sub-agents (a
`change-runner` dispatching `writer` and `reviewer`; a
`review-round-runner` dispatching its `reviewer` fleet). Runtimes that
support nested dispatch allow that nesting. Runtimes that do not leave
a sub-agent without a spawning tool, and nested dispatch past one
level is silently broken on them. One rule lets every recipe run on
both, unchanged.

Where a step says to dispatch a sub-agent via the Task or Agent tool:

- If your context includes such a tool, fan out as written: the named
  specialist (`writer`, `reviewer`, `editor`, `verifier`,
  `review-round-runner`) in its own fresh context.
- If it does not, perform the step yourself by loading the named
  recipe inline: write with the relevant `write-<lang>` recipe or
  `write-tests`, review with the relevant `check-*`, verify with
  `verify-lanes`. Hold the work in your own context and return the
  same contracted line.

Detection is self-inspection of your tool list. No runtime flag is
passed in; no branch lives in the project router.

The inline path costs sequential units (no concurrent fleet) and
self-review (the author reviews its own code). That is the best a
runtime can do where sub-agents cannot spawn sub-agents; the `check-*`
recipes and the verifier lanes carry the quality load. On a runtime
that supports nesting, the fan-out path runs unchanged and neither cost
applies.

This is the only place the adaptation lives. The dispatching skills
(`implement-change`, `run-review-round`) and agents (`change-runner`,
`review-round-runner`) point here; they carry no parallel branching of
their own. One source drives every runtime that supports it; runtimes
without nesting read the same masters and take the inline path. The
mechanism that projects one source across runtimes is covered in
`architecture.md`.

## Autonomy: decide and record, do not stall

The agents run unattended. When a sub-agent meets ambiguity or a
minor block, it does not pause: it makes the best decision it can
with the information it has, records that decision (what it chose,
why, and the alternative it rejected) to the run's decisions log as a
`DECIDED:` line, and moves forward. Standing still is the failure
mode to avoid.

The decisions log lives in the ephemeral checkpoint
(`.claude/runs/<slug>/decisions.edn`); each entry is one terse
record. At the end of the session the entry-point skill presents the
whole log for the maintainer's review, and a decision that proves
architectural is promoted to an ADR via `record-decision`. A
correction the maintainer gives mid-task goes to `capture-guidance`.

A recorded ADR is not overridden. If a task conflicts with an ADR,
the agent follows the ADR (or defers that task), records the
conflict, and continues with the rest of the work.

Stop only when forward progress is genuinely impossible:

- A dependency cannot be satisfied and no reasonable decision
  unblocks it.
- The retry budget is spent (a verifier failed twice on one lane, an
  editor twice on one finding). Record the escalation and continue
  with the rest of the run; it does not halt the campaign.
- The final land. Advancing the protected main line over the linear
  stack is the one gate that always waits for the maintainer; an
  autonomous run never advances main itself.

The maintainer sees the plan up front and the session-end report:
what landed, the decisions log, the escalations, what remains.
Everything between runs without confirmation.

## Campaigns: many changes, unattended

`advance-plan` runs one tier above `implement-change`. It takes a
large chunk (a big vertical slice, or several slices and features from
the implementation plan) and drives it to completion while the
maintainer steps away.

- **Assess and pick.** Read what is already landed (the commit log
  and the modules and tests that exist) and the slice graph in the
  plan; respect the dependency order; take the chunk the maintainer
  named, or the next runnable work.
- **Plan up front, in a sub-agent.** Dispatch a `planner` that runs
  `plan-work`: it decomposes the chunk into phases and tasks, and for
  each task names the planned commit and the specialist that will
  execute it (a `writer` with the relevant `write-<lang>` recipe or
  `write-tests`, a `reviewer`, and so on). The planner writes the
  full plan to the gitignored `.claude/runs/<campaign>/plan.edn` and
  returns a compact summary. The big plan lives on disk, not in the
  campaign's context.
- **Approve once.** Surface the plan summary to the maintainer: the
  one up-front gate. After it, the campaign runs unattended.
- **One change-runner per phase.** Dispatch each phase to a
  `change-runner` that reads its phase from `plan.edn`, runs
  `implement-change` for it in its own context, and returns one
  summary line. The campaign holds the phase checklist and one-line
  outcomes, O(phases), not O(tasks + diffs). The per-phase detail
  never reaches the campaign's context.
- **The phase exit contract.** A change-runner leaves the stack in
  exactly one shape, every time. Before it returns: (a) the phase is
  ONE clean commit per task, and in-round review fixes are folded
  INTO the task commit they belong to, never left as standalone
  `Fix:` or `Refactor:` commits in history (per `write-commit`); (b)
  the working copy is a fresh empty change on the tip (open a new
  change PAST the phase commit, never parked on an interior commit),
  so the next phase stacks cleanly; (c) the final message is ONLY the
  contracted line: a module brief, a context dump, or change-id
  narration as the final message is a contract breach. A runner may
  return `done` only if the tip advanced by at least one non-empty
  commit; otherwise it returns `blocked` with a reason. This contract
  is restated in every dispatch prompt, because a runner that drifts
  from it forces the campaign to hand-repair the stack. Observed
  drifts this contract prevents: the working copy parked on an
  interior commit, fixups left as standalone commits, a dispatch that
  returned a module brief without writing code, and a pre-campaign
  empty base commit wedged into history.
- **Guard the dispatch on a tip advance.** The campaign does not
  accept a runner's `done` on its word. After each return, confirm
  the tip advanced by at least one non-empty commit and the working
  copy is empty; if it did not, the runner bailed (a brief-and-quit,
  a silent early exit). Re-dispatch the phase rather than marking it
  done and dispatching its dependents on an unbuilt base. This
  catches a wasted dispatch at the phase boundary, not three phases
  later. A runner that left the working copy on an interior commit or
  fixups uncollapsed is repaired in place (reposition the working
  copy, fold the fixups) before the next phase, never carried forward.
- **Recover a runner that dies mid-phase; do not trust a silent
  return.** A change-runner that returns NO contract line (a token
  ceiling, a session limit, a crash mid-phase) has not reached the
  clean boundary the exit contract assumes, so its phase is neither
  `done` nor cleanly `blocked`. Do not mark it from the empty return.
  Inspect the stack: read which task commits actually landed (by
  subject and content), then either re-dispatch a fresh runner scoped
  to ONLY the remaining tasks plus the exit-contract finalization,
  or, if just the review round and the fresh-change finalize remain,
  finalize inline (run the round, verify the lanes, leave the working
  copy empty). Never build a dependent phase on the partial, and
  never re-run landed work. This has occurred in practice: a runner
  hit a session limit after its two task commits had landed green but
  before the review and the empty-change finalize, and the campaign
  finished it properly rather than redoing it.
- **When the safety classifier is unavailable for a sub-agent, verify
  its work directly before building on it.** A dispatch can return
  with a note that the classifier could not review the sub-agent's
  output. Treat that as an unproven return: read the sub-agent's
  diffs and run its verification lanes yourself before the next phase
  depends on them, rather than accepting the work on the contract
  line alone.
- **Checkpoint for resume.** Record progress (phases done, in flight,
  open) alongside the plan in the gitignored
  `.claude/runs/<campaign>/` so a crash or a token ceiling does not
  lose the campaign. Resume reads the checkpoint and the plan, not
  the history.
- **Autonomy.** After approval the campaign dispatches every phase
  automatically and runs unattended, per the autonomy model above:
  agents decide and record to unblock, the run stops only for a true
  block or the final land (advancing main), and the session ends with
  a report: what landed, the decisions log, the escalations, what
  remains.

## Working state and resumption

The skill system writes as little to disk as possible. Sub-agents
pass results by return value; the durable record of a change is the
code, the tests, the commit log, the changelog, and any ADR it
settles. Nothing agent-only is committed to the repo.

A long run (a multi-round review, a many-unit change) may keep a
minimal, gitignored checkpoint under `.claude/runs/<slug>/` for one
purpose: so a run interrupted by a crash or a token budget can pick
up where it left off. It records what is done (units landed, rounds
completed, findings still open), not the working data itself. It is
never committed and never the hand-off medium; a run that finishes
cleanly can delete it.

**A drafted-but-unverified working-copy commit is UNVERIFIED on
resume.** When a run resumes after an interruption (a crash, a token
ceiling), the working-copy commit it left mid-task carries no proof
it passed verify and review: the checkpoint records intent, not a
clean result. Never mark its phase done from the draft alone. Re-run
the verify lanes and review it critically before trusting it. This
has caught a real HIGH correctness defect that surfaced only because
the resume re-verified instead of assuming the phase landed. An EMPTY
described commit left by an interruption is filled forward with the
genuinely-uncovered half of its criterion (confirm the gap is real
with a search first), never abandoned (a rewind) and never duplicated
from coverage that already landed (test bloat).

Guidance captured mid-task lives in `.claude/guidance/inbox.edn`
(empty `[]` until the first capture). `incorporate-feedback` drains
it into the durable standards: a reference doc, a skill, or an ADR.
