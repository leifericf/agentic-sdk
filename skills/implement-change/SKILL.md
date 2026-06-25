---
name: implement-change
description: Build one change end to end on the current tip, review-clean and ready to land
disable-model-invocation: true
---

# implement-change

Role: the phase driver. Builds one change or slice from spec to a
review-clean linear stack on the tip, ready for the maintainer to land.

## Prerequisites

`.agentic-sdk/project.edn` exists with `:lanes` and `:vcs`. A spec (argument
or conversation), or an approved feature plan to pick the next slice
from.

## Procedure

Read `skills/shared/references/orchestration.md` (the dispatch protocol,
context budget, autonomy, resumption, runtime adaptation) and
`skills/shared/references/worktree-model.md` (the topology, ordering
law, conflict law) before orchestrating. This skill conforms to them
rather than restating them.

The VCS is jj-first. Work is a linear stack of commits on top of the
current tip. `@` is the working-copy commit; describe it, `jj new` to
advance. There are no development branches. The protected main line is a
bookmark that trails the tip and advances only at land time, by the
maintainer. Never base work on the main bookmark: it forks history
whenever main lags the tip.

1. **Initialize.** Pick a slug. Start the next commit on top of the
   current tip, never on the main bookmark: `jj new` on the current tip,
   then `jj describe -m "<slug>: <one-line summary>"`. The change is the
   next commit (or commits) on the stack, referenced by its change id
   and description, not a bookmark. Hold the one-paragraph change
   summary in context so the rest of the run cites it. A long run may
   keep a minimal, gitignored resume checkpoint under
   `.agentic-sdk/runs/<slug>/` recording what is done (units landed, rounds
   completed, findings still open); it is never the hand-off medium and
   never committed.
2. **Plan units.** First scan the decision index (the ADR store from the
   descriptor) for records the spec touches: a plan that contradicts an
   ADR goes to the maintainer before any dispatch. A real choice made
   while planning, where the rejected alternative would have been
   reasonable, gets recorded via record-decision. With no explicit spec,
   the spec is the next runnable slice from the approved feature plan:
   respect the dependency graph, take that slice's planned commits as
   the starting unit breakdown. Split the spec into units, each owning
   one module. Dispatch gather-module-context once per module for the
   whole phase and hold the brief in context, to embed into both the
   writer dispatches in step 3 and every review-round-runner dispatch in
   step 6. Every unit gets a test unit and an implementation unit.
   State each unit's spec in three to six lines. Size every unit to be
   completable by one agent in one sitting: if a unit needs a second
   dispatch to finish, it was two units.
3. **Write.** Test units first, implementation units second. Within each
   wave, dispatch in module-batches: one batch is all modules with
   independent work at that wave, run in parallel via the Agent tool.
   Wait for the batch, collect the writers' returns (`LANDED <change-id>`
   plus any `CHANGELOG:` lines; hold both in context), then the next
   wave. Each dispatch prompt carries the unit's complete spec plus its
   module brief: self-contained, so the writer never needs this
   session's context. The writer loads the write-<lang> recipe for the
   active language (write-c, write-zig, write-clj, write-elixir) or
   write-tests. Where the runtime supports nested dispatch, fan out as
   written; where it does not, perform the step inline per the
   adaptation rule in orchestration.md.
4. **Integrate.** Inline mode: writers committed in module order (leaf
   modules first, then dependents, then the app or top-level
   composition; tests before their implementations within each module),
   nothing to fold. Worktree mode: fold each writer's change into the
   stack in module order, tests before their implementations. A writer
   that returned `needs-cross-module` becomes one fresh writer with the
   union of the cross-module work, folded in after, then continue.
5. **Verify.** One verifier agent on the stack, running verify-lanes
   against the project's lanes (from the descriptor): the cheap tier on
   every unit. FAIL feeds back to an editor and re-verifies, twice max,
   then escalates and the round continues.
6. **Review rounds, capped at two.** Dispatch one review-round-runner on
   the stack scope, embedding the per-module briefs from step 2 in its
   dispatch prompt. It runs run-review-round: fans out the active
   check-* dimensions over the scope, runs the spine triage task,
   dispatches editor waves by level via apply-findings, and re-verifies.
   Run a second round only if a high- or medium-severity correctness or
   security finding remains open after the first, or the phase touches
   three or more modules, a native edge between languages, or untrusted
   input. Never more than two rounds. Low-severity and style-only
   findings that a would-be third round would raise are not a new round:
   record them as forward tasks in the decisions log per the
   forward-only law. Each round returns exactly one summary line; hold
   those lines in context.
7. **Land.** The history is already a linear stack on the tip; nothing
   to restack. Update the changelog via write-changelog: create the
   `## Unreleased` heading if absent; append each `CHANGELOG:` line the
   writers and round-runners returned, grouped by category prefix.
   Describe that change via write-commit. Then show the maintainer: the
   stack head, the commit list, the changelog diff, the round
   summaries, and any escalations held. Advancing main over the stack is
   the maintainer's call: offer, do not assume. An autonomous run never
   advances main itself. On a yes, the maintainer advances the bookmark
   over the linear stack and pushes. A clean run can delete its resume
   checkpoint.

## Boundaries

Reads only the one-line returns of the writers, the verifier, and the
review-round-runner it dispatches, never their diffs, file bodies,
findings dumps, or reasoning. The working data lives in those agents'
contexts and is thrown away when they return. Holds the slug, the
change summary, the writers' `LANDED` and `CHANGELOG:` lines, and the
round summaries. The final land waits for the maintainer. Atoms
dispatched: gather-module-context (once per module), writer
(write-<lang>, write-tests), verifier (verify-lanes), review-round-runner
(run-review-round, which fans out the active check-* dimensions, runs
the spine triage task, and dispatches editor waves via apply-findings),
record-decision for real choices, write-commit and write-changelog at
the land.

## Return

A one-line summary to the caller (the maintainer, or a change-runner
under a campaign): the change landed, the round count, the verify
verdict, and any escalations, plus the stack head ready for the
maintainer to land.
