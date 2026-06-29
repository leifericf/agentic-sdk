---
name: implement-change
description: Build one change end to end on the current tip, review-clean and ready to land
disable-model-invocation: true
---

# implement-change

Role: the phase driver. Builds one change or slice from spec to a
review-clean linear stack on the tip, ready for the maintainer to land.

## Prerequisites

`~/.agentic-sdk/<project>/project.edn` exists with `:lanes` and `:vcs`. A spec
(argument or conversation), or an approved feature plan to pick the next
slice.

## Procedure

Conforms to `skills/shared/references/orchestration.md` (dispatch,
context budget, autonomy, resumption, runtime adaptation) and
`skills/shared/references/worktree-model.md` (topology, ordering law,
conflict law).

jj-first. Work is a linear stack of commits on the current tip; `@` is
the working-copy commit (describe it, `jj new` to advance). No
development branches. The protected main line is a bookmark trailing the
tip, advanced only at land time by the maintainer. Never base work on
it: it forks history when main lags the tip.

1. **Initialize.** Pick a slug. Start the next commit on the current
   tip, never on the main bookmark: `jj new` on the tip, then `jj
   describe -m "<slug>: <one-line summary>"`. The change is the next
   commit (or commits) on the stack, referenced by change id and
   description, not a bookmark. Hold the one-paragraph change summary in
   context for the rest of the run to cite. A long run may keep a
   minimal, gitignored resume checkpoint under
   `~/.agentic-sdk/<project>/runs/<slug>/` recording what is done (units landed,
   rounds completed, findings still open); never committed, never the
   hand-off medium.
2. **Plan units.** Scan the decision index (the ADR store from the
   descriptor) for records the spec touches first: a plan that
   contradicts an ADR goes to the maintainer before any dispatch. Record
   a real choice (where the rejected alternative was reasonable) via
   record-decision. With no explicit spec, the spec is the next
   runnable slice from the approved feature plan: respect the
   dependency graph, take its planned commits as the starting unit
   breakdown. Split the spec into units, each owning one module.
   Dispatch gather-module-context once per module for the whole phase
   and hold the brief, to embed into the writer dispatches in step 3
   and every review-round-runner dispatch in step 6. Every unit gets a
   test unit and an implementation unit. State each unit's spec in
   three to six lines. Size every unit to one agent in one sitting: if
   it needs a second dispatch to finish, it was two units.
3. **Write.** Test units first, implementation units second. Within
   each wave, dispatch in module-batches: one batch is all modules with
   independent work at that wave, run in parallel via the Agent tool.
   Wait for the batch, collect the writers' returns (`LANDED
   <change-id>` plus any `CHANGELOG:` lines; hold both in context),
   then the next wave. Each dispatch prompt carries the unit's complete
   spec plus its module brief, self-contained so the writer never needs
   this session's context. The writer loads write-<lang> for the active
   language (write-c, write-zig, write-clj, write-elixir) or
   write-tests. Fan out where the runtime supports nested dispatch,
   else run inline per the adaptation rule in orchestration.md.
4. **Integrate.** Inline mode: writers committed in module order (leaf
   modules first, then dependents, then the app or top-level
   composition; tests before their implementations within each module),
   nothing to fold. Worktree mode: fold each writer's change into the
   stack in module order, tests before implementations. A
   `needs-cross-module` writer becomes one fresh writer with the union
   of the cross-module work, folded in after, then continue.
5. **Verify.** One verifier on the stack running verify-lanes against
   the project's lanes (from the descriptor): the cheap tier on every
   unit. FAIL feeds back to an editor and re-verifies, twice max, then
   escalates and the round continues.
6. **Review rounds, capped at two.** Dispatch one review-round-runner
   on the stack scope, embedding the per-module briefs from step 2 in
   its prompt. It runs run-review-round: fans out the active check-*
   dimensions over the scope, runs the spine triage task, dispatches
   editor waves by level via apply-findings, and re-verifies. Run a
   second round only if a high- or medium-severity correctness or
   security finding remains open after the first, or the phase touches
   three or more modules, a native edge between languages, or untrusted
   input. Never more than two. Low-severity and style-only findings a
   would-be third round would raise are not a new round: record them as
   forward tasks in the decisions log per the forward-only law. Each
   round returns exactly one summary line; hold those lines in context.
7. **Land.** The history is already a linear stack on the tip; nothing
   to restack. Update the changelog via write-changelog: create the
   `## Unreleased` heading if absent; append each `CHANGELOG:` line the
   writers and round-runners returned, grouped by category prefix.
   Describe the change via write-commit. Then show the maintainer: the
   stack head, the commit list, the changelog diff, the round
   summaries, and any escalations held. Advancing main is the
   maintainer's call: offer, do not assume. An autonomous run never
   advances main itself. On a yes, the maintainer advances the bookmark
   over the stack and pushes. A clean run can delete its resume
   checkpoint.

## Boundaries

Reads only the one-line returns of the writers, verifier, and
review-round-runner it dispatches, never their diffs, file bodies,
findings dumps, or reasoning. The working data lives in those agents'
contexts, thrown away when they return. Holds the slug, the change
summary, the writers' `LANDED` and `CHANGELOG:` lines, and the round
summaries. The final land waits for the maintainer. Atoms dispatched:
gather-module-context (once per module), writer (write-<lang>,
write-tests), verifier (verify-lanes), review-round-runner
(run-review-round), record-decision for real choices, write-commit and
write-changelog at the land.

## Return

A one-line summary to the caller (the maintainer, or a change-runner
under a campaign): the change landed, the round count, the verify
verdict, any escalations, and the stack head ready to land.
