---
name: run-review-round
description: The authoritative procedure for one review round: lanes, reviewer fan-out, spine triage, level-ordered editor waves, integrate, verify, advance.
user-invocable: false
---

# run-review-round

One round: everything from a stable stack to that stack plus this
round's findings fixed and verified. Run by the `review-round-runner`
agent.

The round's clerical work is owned by the deterministic spine; the
skill calls the spine tasks by name, which answer at the project's
spine-presence level (full, thin, or none, per `references/spine.md`).
At full spine they fold the working dir deterministically; at thin or
none the round-runner folds the same data by return value, keeping the
discipline intact. Sub-agent dispatch follows the runtime adaptation
rule in `references/orchestration.md`: fan out where the Task tool
exists, else load the named recipe (`check-*`, `apply-findings`,
`verify-lanes`) inline.

Inputs from the dispatch: the stack (referenced by its head's change
id, a stack head not a branch), the scope (module shards), the round
number, and the run slug if the run keeps a resume checkpoint.

## Working state

The round carries its working state in the spine working dir
(`.<project>/`, per `references/spine.md`) at full spine, and in your
context at thin or none: the reviewer findings written to the
`findings/` pool; the punch list the `triage` task produces; the editor
returns (`LANDED`, `needs-cross-module`, `FAILED`) and changelog lines
you collect; the escalations list, started empty and grown by a double
`FAILED` on one finding; for `audit-code`, the out-of-scope items set
aside. A long run may keep a minimal gitignored resume checkpoint under
`.agentic-sdk/runs/<slug>/` recording only what is done; never
committed, never the hand-off medium.

## Batching

Fan out in module-batches, not one giant wave. A batch is one module's
worth of dispatches, run in parallel; wait for the batch, collect the
returns, then dispatch the next. Reviewers: one batch per shard, one
reviewer per applicable dimension (typically 3 to 7 concurrent).
Editors: one batch per level and wave, one editor per module with
findings at that level (typically 1 to 5 concurrent). Keep concurrency
modest, around 5 to 7 per batch; split a larger batch sequentially.
This bounds both concurrency and orchestrator context (N compact
returns per batch, not 50 at once).

## Procedure

1. **Deterministic lanes first; their findings are free.** Run the
   `lint` spine task (`bb lint` on the Babashka adapter today) over the
   changed files, and the cheap lane set from `verify-lanes` on the
   stack. Convert each hard failure into a finding (dimension and level
   per the defect) and add it to the findings pool. Lanes not yet wired
   report `SKIP` and produce no findings; note them in the round
   summary.
2. **Reviewer fan-out, module-batches.** For each shard, compute
   `gather-module-context` once and embed the brief in every dispatch
   in that batch (reuse briefs the change-runner already shared across
   rounds). Dispatch one `reviewer` per dimension via the Agent tool,
   all in parallel within the batch. Each reviewer writes its findings
   to the `findings/` pool (or returns them inline at thin or none) and
   returns `NO FINDINGS` when clean. Dimensions are fixed per module
   type, the floor not a ceiling:
   - **pure core** (FC/IS core): `:correctness`, `:factoring`,
     `:style`. Add `:design` for any UI or view-spec shard.
   - **shell** (persistence, platform, app composition): the pure-core
     set plus `:conformance`.
   - **native or renderer** (C ABI, NIF, the foreign-function edge):
     the shell set plus `:security`, `:performance`, `:portability`.
     Add `:memory` for C and Zig.

   A reviewer or this runner may add a dimension the floor omits when
   the change clearly warrants it (a pure-core change that parses
   untrusted bytes pulls in `:security`), recorded as a decision.
3. **Triage.** Run the `triage` spine task (`bb triage` today): it
   dedupes on `[file evidence rule]`, drops rule-less opinions to the
   query list, drops findings whose evidence carries a protected idiom,
   orders by editing level then severity then file, and renumbers as
   `FINDING-1`, `FINDING-2`, writing `triage/punch-list.edn` and
   `triage/punch-list.md`. At thin or none, fold the same way by hand
   from the collected returns. The punch list is for structure, not
   nits: a cosmetic finding a lane already gates is dropped here, not
   turned into an editor wave; a genuine low-severity style point no
   lane gates becomes a forward task, never its own wave. Zero items:
   go to step 8 with found-new false.
4. **Editor waves, level order.** For each level present (correctness,
   then factoring, then style):
   - Group punch-list items by module. Dispatch one `editor` per module
     in parallel (one batch per level), passing that module's items in
     the dispatch prompt. Inline mode for small scopes; worktree mode
     per `references/worktree-model.md` when three or more independent
     modules are involved and the diff warrants it (dispatch with
     workspace isolation so each editor gets its own workspace).
   - Collect the batch's returns. `needs-cross-module` items: hold
     until this level's module waves finish, then dispatch one editor
     with the union on its own change.
   - Hold each editor's `CHANGELOG:` lines into the round's changelog
     set. The runner does this, not the editors or a spine. Never edit
     `CHANGELOG.md` mid-round; the run merges the set at its land
     step.
   - Escalations: a `FAILED` return retries once; a second failure on
     the same finding adds an escalation entry and stops work on that
     item, leaving it for the maintainer.
   - Barrier: do not start the next level until this wave verified
     clean (step 6).
5. **Integrate.** Run the `integrate` spine task (`bb integrate`
   today): it cherry-picks each editor's fix branch onto the working
   branch oldest-first and reports conflicts without guessing (editors
   own disjoint files within a level, so the common case is
   conflict-free). Inline mode needs no integrate; editors folded their
   commits into the stack directly. For any isolated workspaces, run
   `jj workspace forget <name>`; escalated changes stay until the
   maintainer decides.
6. **Verify the wave.** One `verifier` on the stack: the cheap set
   after correctness or factoring waves; the full landing set after the
   last wave of the round (`verify-lanes`). A FAIL feeds back: triage
   the failure as a finding, dispatch an editor, re-verify, at most
   twice before recording an escalation and stopping the round.
7. **Found-new flag.** True iff triage produced one or more items this
   round, regardless of how many were fixed.
8. **Advance.** Run the `run` spine task (`bb run status` today) to
   mark the round complete and compute the next directive
   (`:next-round` when this round found new findings and the cap is
   not hit, else `:complete`). If the run keeps a resume checkpoint,
   mark the round done in it. Compose and return the round summary.
   The round's changelog lines merge into `CHANGELOG.md` only when the
   whole run lands, not here.

The finding shape (dimension, severity, level, file, evidence,
suggestion, rule) is fixed in `references/review-model.md`; reviewers
write one finding map per alert into the shared pool the `triage` task
consumes.

## Boundaries

Owns exactly one round. The phase exit, the round cap, and the campaign
are `implement-change` and `advance-plan`; per-finding fixing is
`apply-findings`; the lanes are `verify-lanes`. Reviewers are
read-only, the editor is the sole mutator, the verifier has no
judgment, per `references/review-model.md`.

## Return

`ROUND <n>: <f> findings, <x> fixed, <e> escalated, <d> decisions, verify <PASS|FAIL>, <continue|dry>`
