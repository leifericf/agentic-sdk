---
name: plan-work
description: Decompose a chunk of the approved plan into a forward-only DAG of phases and tasks, write it to plan.edn, return a compact summary
user-invocable: false
---

# plan-work

Role: the planning specialty `advance-plan` dispatches before it drives.
Decomposes one chunk of the approved feature plan into a forward-only DAG
of phases and tasks, writes the full plan to the run's `plan.edn`, and
returns a compact summary. The big plan lives on disk; only the summary
rides back in context. It plans; it does not write source or dispatch
other agents.

Input: a named chunk of the approved feature plan, plus the run slug.
Output: the full plan at `.agentic-sdk/runs/<slug>/plan.edn` and a
compact summary returned to the caller.

## Stance

Read `skills/shared/references/orchestration.md` before planning; its
laws bind this work. Four applications matter here:

- **Context is the budget.** The plan can be large. It goes to
  `plan.edn`, never into the return. The return is the phase list, the
  task counts, the critical path, and the conflicts, nothing more. A
  planner that returns the full plan defeats the reason it runs in a
  sub-agent.
- **Forward-only: a DAG, not a loop.** Decompose into phases and tasks
  that flow one direction. A phase lands before any phase that depends
  on it starts. Order to minimise work in progress. Never plan a step
  that backtracks into landed work to make later work fit; if an
  earlier decision proves wrong, that is a new forward task, not a
  rewind.
- **Autonomy: decide and record.** Where the feature plan leaves a
  choice open, pick the recommended option, note the pick in the task's
  `:desc` and the plan's `:notes`, and plan the task to settle it. Do
  not stall on an open choice; the plan records the decision and moves
  on.
- **Shift security and verification left.** Each task carries its own
  test layers as part of its definition of done. Where a task takes
  untrusted input or crosses a native edge between languages, its
  `:done` names `check-security` and the relevant verify lanes, not a
  gate bolted on at the end.

## Procedure

1. **Assess what is landed.** Do not trust the feature plan to tell you
   where the project is; read the ground truth:

   - `jj log -r 'latest(::@, 40)'` (or `jj log` over the repo) for the
     commit history. The category-first messages map to the plan's
     planned-commit lists; match them to see which slices and commits
     already landed.
   - The project's lanes and module map (from `.agentic-sdk/project.edn`)
     and the module subtrees on disk for which modules exist; the test
     suites for which tests exist. A slice is landed when its modules
     and its owning tests are present and its planned commits show in
     the log, not when the plan lists it.
   - The ADR store (the descriptor's `:adr`; treat a missing index as
     `[]`) for decisions the chunk touches. A task that would contradict
     an ADR is a conflict: record it in the plan's `:notes` and plan
     around the ADR, never plan to violate it.

2. **Pick the chunk in dependency order.** Take the chunk the caller
   named. Confirm every dependency of every slice in it is landed, using
   the feature plan's dependency graph. Never plan a phase whose
   dependencies are not landed. If a named slice has an unlanded
   dependency outside the chunk, record the gap in `:notes` and plan
   only the runnable part.

3. **Decompose into a forward-only DAG.**

   - **Phases.** Each phase maps to a slice, or to a coherent sub-slice
     group (the test-infrastructure setup, the native wrappers, the UI
     widgets, the wiring). A phase has an `:id`, a `:title`, its `:deps`
     (phase ids that must land first), and its `:tasks`. Order phases
     topologically so work flows one direction.
   - **Tasks.** Decompose each phase into tasks following the slice's
     planned-commit list and the TDD choreography: scenario red, unit
     tests red, then implementation green in the same commit as the test
     it flips, then refactor. The unit of work is a feature slice across
     languages, never a language layer. A native edge (the wrapper on
     one side, the native body on the other, its round-trip test, and
     its resource-leak assertion) is one task owned end to end.

   For each task, name all fields:

   - `:commit` - the planned commit message, category-first per
     `write-commit` (single line, capitalized, imperative, within 70
     characters, no trailing period). Draw from the slice's
     planned-commit list where one fits; author a new one in the same
     form where the decomposition needs it.
   - `:agent` - the specialist that executes it: `writer` for code and
     tests, `reviewer` for a review pass.
   - `:skill` - the recipe that specialist applies:
     - `write-c`, `write-zig`, `write-clj`, or `write-elixir` for the
       active language (pure core, imperative shell, native wrappers)
     - `write-ui` for view-spec, layout, hit-test, widget, and renderer
       surfaces
     - `write-tests` for any test layer (unit, property,
       bounded-exhaustive, edge-value, negative-space, boundary fuzz,
       integration and lifecycle, end-to-end)
     - a `check-*` recipe for a `reviewer` task (`check-security`,
       `check-correctness`, `check-performance`, `check-portability`,
       `check-memory`, and the rest of the active dimensions)
   - `:deps` - the task ids (and phase ids) that must land first. Tests
     that state a spec land before the implementation that flips them
     green; a wrapper lands before the test that calls real native code
     through it.
   - `:done` - the definition of done: which test layers from
     `references/pyramid.md` prove the task, plus `check-security` and
     the verify lanes where untrusted input or a native edge is
     involved. Be specific: name the layers and lanes, not "tests
     pass".

   Order so work flows one direction with minimal work in progress and
   no backtracking. A phase's last tasks are its verification and
   coverage floor.

4. **Write `plan.edn`.** Write the full plan to
   `.agentic-sdk/runs/<slug>/plan.edn` (create the directory). This is
   the ephemeral resume and execution artifact: `.agentic-sdk/runs/` is
   gitignored; never commit it; it is never the hand-off medium (the
   return is). The shape:

   ```clojure
   {:campaign "<slug>"
    :chunk    "the named chunk from the feature plan"
    :assessed {:landed   ["the slices and spikes already on the tip"]
               :modules  ["the module paths from project.edn"]
               :adrs     ["ADR titles the chunk touches"]}
    :critical-path ["p1" "p2" "p4" "p7"]
    :phases
    [{:id    "p1"
      :title "Module scaffolding and deps"
      :deps  []
      :tasks
      [{:id     "t1"
        :desc   "Add module paths and deps for the slice"
        :commit "Scaffold: Add module paths and deps for the slice"
        :agent  "writer"
        :skill  "write-clj"
        :deps   []
        :done   ["cheap lane green"]}
       {:id     "t2"
        :desc   "Native body and wrapper for the decode edge"
        :commit "Feat: Add native decode body and wrapper"
        :agent  "writer"
        :skill  "write-zig"
        :deps   ["t1"]
        :done   ["cheap lane green" "check-security" "leak lane"]}
       ;; ...
       ]}
     ;; ...
     ]
    :notes ["an open choice the plan settled, recorded for the campaign"]}
   ```

   Field contract: each phase has `:id` `:title` `:deps` `:tasks`; each
   task has `:id` `:desc` `:commit` `:agent` `:skill` `:deps` `:done`.
   `:critical-path` is the longest dependency chain by task id.
   `:assessed` records what step 1 found. `:notes` holds open choices
   made, ADR conflicts, and deferred work.

5. **Return the compact summary only.** Never return the full plan.
   Return:

   - one line per phase: `<id> <title> - <task-count> tasks, deps <ids>`
   - total task count and total phase count
   - the critical path as a task-id chain
   - any conflict or deferral worth the campaign's attention (an ADR
     conflict, an unlanded out-of-chunk dependency), one line each, or
     `no conflicts`

   The campaign holds this; the full plan stays on disk for the
   change-runners to read per phase.

## Boundaries

Owns decomposing a chunk into a forward-only DAG and writing `plan.edn`.
`change-runner` owns executing one phase of that plan end to end. Does
not edit source; `writer` and `editor` do. Does not dispatch other
agents; `advance-plan` drives the campaign.

Return contract: the compact summary (one line per phase, totals,
critical path, conflicts or deferrals), then
`PLAN .agentic-sdk/runs/<slug>/plan.edn`.

## References

- The approved feature plan under `.agentic-sdk/artifacts/planning/tasks/`
  (dependency graph, deliverables, planned-commit lists, per-slice test
  layers).
- `skills/shared/references/orchestration.md` (context as budget,
  forward-only DAG, autonomy, the campaign flow this planner feeds).
- `skills/shared/references/pyramid.md` (the test taxonomy `:done`
  fields name).
- `verify-lanes`; the `write-<lang>`, `write-ui`, and `write-tests`
  recipes; the active `check-*` dimensions (what `:done` and `:skill`
  fields cite).
- `record-decision`, for an open choice the plan settles that proves
  architectural.
