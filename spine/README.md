# Spine

The deterministic spine for the agentic-sdk toolkit, ported and generalized
from a prior prose-production system's spine. The model: mino tasks own the
clerical work; agents write EDN to a working dir; tasks fold deterministically;
collisions escalate, never guess; code never parses its own rendered output.
Prose-only tasks are out of scope.

For the human-facing reference, see `skills/shared/references/spine.md`
(authored separately; this file is the task interface).

## Tasks

All tasks take a target root (default `.`) and are invoked via the `agentic`
CLI (a mino script), which delegates to the namespaces below.

| Task | What it does |
|---|---|
| `agentic triage [ROOT]` | Fold `findings/*.edn` into one ordered punch list. Dedup on file+evidence+rule, drop protected idioms, convert rule-less opinions to queries, order by editing level then severity then file, renumber FINDING-N. Writes `triage/punch-list.edn` and `.md`, consumes the inputs. |
| `agentic integrate [ROOT]` | Land parallel fix branches onto the working branch oldest-first (jj rebase or git cherry-pick via the core adapter). Abort and report any conflict; delete consumed branches. |
| `agentic resume init\|status\|advance [ROOT] [EDN]` | Resumption state. `init` seeds `run.edn` from the plan and descriptor; `advance` deep-merges an EDN updates map (maps one level, scalars replace); `status` prints the next directive and exits 0 only when complete. |
| `agentic rules compile [ROOT] [DIR]` | Project `decisions.edn` (banned categories, naming rulings, commit categories) into a deterministic lint config under DIR (default `<working-dir>/rules`). One-way projection. |
| `agentic lint [--edn PATH] [FILE...]` | House prose regex pre-pass (bans the em-dash, ASCII arrows in prose, plan/task process IDs, ASCII banners) over `.md`/`.mdx`/`.txt`, plus a detected project linter (clj-kondo, credo, clang-tidy, cppcheck). Lifts every finding into the canonical shape. |
| `agentic agents sync [ROOT]` | Project the agent masters (`.claude/agents/`, or `agents/` in this repo) into `.opencode/agent/` in OpenCode format. |
| `agentic agents check [ROOT]` | Exit non-zero and list derived agent files that are stale vs their masters. Never hand-edit the derived form. |
| `mino task test` | Run the spine test suite. |

## Working-dir format

Default `<project-home>/state/` (overridable via the descriptor
`:spine :working-dir`). Created on first write; never assumed on read.

```
state/
  findings/            reviewers and lint write *.edn here (the only producers)
    *.edn
  triage/
    punch-list.edn     the folded, ordered, renumbered list (record of truth)
    punch-list.md      the rendered view
  rules/
    lint-rules.edn     banned patterns, from agentic rules compile
    commit-categories.edn
  run.edn              the resumption checkpoint
  escalation.edn       collisions that need a human, never auto-resolved
  protected-idioms.edn optional backstop list, dropped from triage
```

## Canonical finding shape

Every producer (reviewers, lint) writes maps with these flat keys:

```edn
{:dimension   :correctness    ; one of the catalog dimensions
 :level       :correctness    ; :correctness | :factoring | :style (reporter sets)
 :severity    :high           ; :high | :medium | :low
 :file        "src/x.c"
 :line        42              ; optional, used for ordering and display
 :evidence    "nil deref of p"
 :suggestion  "guard null"    ; nil when none
 :rule        "nil-check"     ; blank rule makes the finding a query, not a fix
 :reporter    "reviewer-1"}
```

The lint task emits its own uppercase severity vocabulary; `triage` normalizes
those to `:high`/`:medium`/`:low` before ordering, so producers may stay in
either vocabulary.

## Editing-level mapping (software)

`triage` orders findings by level, then severity, then file. Reviewers carry
their own `:level`; lint and render do not, so triage falls back to a
dimension-to-tier map. The tier ordering is:

- correctness
- factoring
- style
- lint and render

## Directives from `agentic resume status`

`next-directive` computes one action from the on-disk state:

- `{:action :run-stage :stage ...}` when a stage in the current round is not done.
- `{:action :next-round :round N}` when the round is under cap and new findings appeared.
- `{:action :next-phase :phase ...}` when the rounds for a phase are exhausted and a phase remains pending.
- `{:action :complete}` otherwise.

`status` also reports pending collisions, pending phases, and whether the
plan or descriptor changed since `init` (gate arming).

## Invariants (preserved in spirit from the prior system)

- Escalate, do not guess. An ambiguous fold appends to `escalation.edn`; a
  human resolves it.
- Order-independent folding. `triage` groups before it sorts, so the order
  reviewers wrote findings does not change the punch list.
- Single parser. `core/read-edn` is the only reader; rendered output
  (punch-list.md) is never parsed back.
- Never re-run a failed lane. A non-zero stage stays non-done until the
  orchestrator advances it on purpose.
- jj-first VCS. The adapter prefers jj, falls back to git.
