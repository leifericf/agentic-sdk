# The deterministic spine: interface contract

Status: **Authoritative interface.** The spine is the clerical layer the model
used to do by hand: triage ordering, parallel-fix integration, resumption
state, rule projection, mechanical lint. It is core and always present. This
document fixes the task names and the working-directory format they read and
write. The implementation behind these names swaps; the calls do not.

## The stable interface

The skill and agent layer calls **task names**, never the runtime. Today the
runtime is Babashka and the store is EDN files under the working dir. Tomorrow
a future static-binary task runtime replaces Babashka and a future
immutable-fact store replaces EDN files as the store. The task names, their
arguments, their reads, their writes, and their exit contracts stay fixed. A
skill that calls `triage` today calls `triage` after the migration; only the
adapter that answers the call changes.

Concretely: a recipe says "run the `triage` spine task over the working dir"; it
does not say `bb triage`. The host runtime (or a thin dispatch shim) resolves the
name to the active adapter. This is why a C project at `:spine :runtime :thin`
and a Clojure project at `:spine :runtime :babashka` invoke the same name and
get the same contract.

## Task catalog

Seven tasks. Each entry fixes: the **invocation** (name and arguments), what it
**reads** (input paths and EDN), what it **writes** (output paths and EDN), the
**invariant** it owns, and the **exit contract** (one line on stdout, exit code).

Arguments use `[ROOT]` for the project root, defaulting to `.`. Paths inside the
working dir are relative to `:spine :working-dir` (shown here as
`.agentic-sdk/.spine/`).

### `triage`

- **invocation:** `triage [ROOT]`.
- **reads:** every `.agentic-sdk/.spine/findings/*.edn` (each a finding map or a vector of
  finding maps, sorted by filename for deterministic order);
  `.agentic-sdk/.spine/protected-idioms.edn` (a vector of idiom strings) when present.
- **writes:** `.agentic-sdk/.spine/triage/punch-list.edn` and
  `.agentic-sdk/.spine/triage/punch-list.md`.
- **invariant:** dedupe on `[file evidence rule]`, drop rule-less opinions (they
  become queries), drop findings whose evidence contains a protected idiom, order
  by editing level then severity then file order, renumber as `FINDING-1`,
  `FINDING-2`, and so on. Pure and deterministic: same findings in, same punch
  list out.
- **exit contract:** prints `triage: N findings, M queries`; exit `0` clean, `1`
  findings present, `2` usage.

Level discipline: triage honors the reporter's `:level` (`:correctness`,
`:factoring`, `:style`); findings without `:level` (lint, render) fall back to
a dimension-to-tier map. Ordering tiers: correctness, then factoring, then
style, then lint. The lint task emits its own uppercase severity vocabulary;
triage normalizes those to `:high`/`:medium`/`:low` before ordering.

### `integrate`

- **invocation:** `integrate [ROOT]`. Opts (passed by the orchestrator, not on
  the command line): `:working-branch`, `:prefix`, `:delete-branches?`.
- **reads:** the working branch (default the current HEAD); every fix branch
  under the prefix (default `.agentic-sdk/.spine/fix/`),
  sorted oldest first; the commits ahead of working on each.
- **writes:** the landed commits cherry-picked onto the working branch; deletes
  the consumed fix branches when `:delete-branches?` is true (the default).
- **invariant:** cherry-pick each fix branch's commits oldest first; on conflict,
  abort that cherry-pick and report the conflict, never guess. Because editors
  own disjoint files within a level, the common case is conflict-free.
- **exit contract:** prints `integrate: N landed, M conflicts`; exit `0` clean,
  `1` conflicts present.

### `run`

Resumption state. Three subforms, all under one task name.

#### `run init`

- **invocation:** `run init [ROOT] [EDN-OPTS]`. Opts: `:mode` (default
  `:campaign`), `:round-cap`
  (default `3`), `:units` (default every unit).
- **reads:** the plan and the descriptor (the unit list and statuses).
- **writes:** `.agentic-sdk/.spine/run.edn`.
- **invariant:** seeds a minimal checkpoint: scope, the stage map (every stage
  `:pending`), round `0`, per-unit status, and the sha256 of the gate-arming
  inputs (the plan and the descriptor). Not a state engine; the orchestrator
  stays near-stateless.

#### `run status`

- **invocation:** `run status [ROOT]`.
- **reads:** `.agentic-sdk/.spine/run.edn`, the plan,
  `.agentic-sdk/.spine/escalation.edn`,
  and the current hash of the gate-arming inputs.
- **writes:** nothing. Prints the directive map.
- **invariant:** compute the single next directive (run the first pending stage,
  start the next round when a round found new findings and the cap is not hit,
  advance to the next phase when the rounds for this one are exhausted and a
  phase remains pending, else complete), plus the bounded signals: collisions
  pending, stale units, and whether the gate-arming inputs changed since init.

#### `run advance`

- **invocation:** `run advance [ROOT] [EDN]`. The EDN is a partial update map.
- **reads:** `.agentic-sdk/.spine/run.edn`.
- **writes:** `.agentic-sdk/.spine/run.edn`, deep-merged. Maps merge one level (so
  `{:stages {:lint :done}}` updates only that stage); other values replace.
- **invariant:** the orchestrator advances the checkpoint after each phase and
  reads `status` to learn the next directive, never the transcript.

The directive actions are `:run-stage`, `:next-round`, `:next-phase`,
`:complete`. Exit `0` when the directive is `:complete`, `1` otherwise, `2`
usage.

### `compile-rules`

- **invocation:** `compile-rules [ROOT] [STYLES-DIR]`. The styles dir defaults
  to the working dir's `rules/`.
- **reads:** `.agentic-sdk/.spine/decisions.edn` (banned categories, naming rulings,
  commit categories).
- **writes:** under `STYLES-DIR`: `lint-rules.edn` (the banned-pattern list as
  rule maps) and `commit-categories.edn` (the allowlist).
- **invariant:** one-way deterministic projection. Same inputs, same bytes.
  `decisions.edn` is the single home of the project's banned-idiom and naming
  rulings; code never parses its own rendered output.
- **exit contract:** prints `wrote <path>` per file; exit `0`.

The output is EDN that `lint` loads directly. A project that also runs a
language-native linter (clj-kondo, credo, clang-tidy) keeps that config
separate; `compile-rules` owns only the project's house rules.

### `lint`

- **invocation:** `lint [--edn PATH] FILE...`. The `--edn PATH` flag writes the
  lifted findings as an EDN vector alongside the human-readable output.
- **reads:** the named files. Two layers, no external runtime dependency (no
  Vale): a house regex pre-pass over prose files (`.md`/`.mdx`/`.txt`) banning
  the em-dash, ASCII arrows in prose, plan/task process IDs, and ASCII banner
  lines (code-fence aware); plus a detected project linter (clj-kondo, credo,
  clang-tidy, cppcheck) when one is inferable from the descriptor's `:lanes` or
  common configs. The house rules from `compile-rules` (`lint-rules.edn`) are
  loaded when present.
- **writes:** when `--edn` is given, the EDN vector at `PATH` (one finding map
  per alert, dimension `:lint`); always, the line-form findings on stdout.
- **invariant:** zero model tokens, zero binary deps. Every finding is lifted
  into the shared finding shape so it joins the same `findings/*.edn` pool every
  reviewer writes.
- **exit contract:** prints `file:line|SEVERITY|id|message` per finding; exit `0`
  clean, `1` findings, `2` hard error (a detected linter failed to run).

### `opencode-sync`

- **invocation:** `opencode-sync [ROOT]`.
- **reads:** the agent masters under `.agentic-sdk/agents/` (or `agents/` in
  this repo).
- **writes:** the projected OpenCode form under `.opencode/agent/`.
- **invariant:** one-way deterministic projection of the masters into the
  OpenCode format. Masters are never hand-edited in the derived form. See
  `docs/design.md` section 12 for the runtime-port contract this task serves.
- **exit contract:** prints `opencode-sync: wrote N agents`; exit `0`.

### `opencode-check`

- **invocation:** `opencode-check [ROOT]`.
- **reads:** the agent masters and the derived `.opencode/agent/` form.
- **writes:** nothing.
- **invariant:** fail when any derived agent file is stale against its master.
  Running `opencode-sync` is the fix. Pairs with `opencode-sync` as the
  verify-lanes-style gate for the runtime port (section 12).
- **exit contract:** prints `opencode-check: N stale`; exit `0` clean, `1`
  stale, `2` usage.

## Working-directory format

The gitignored `.agentic-sdk/.spine/` dir (path from `:spine :working-dir`,
default `.agentic-sdk/.spine/`). Every path below is relative to it. The store
is EDN today, a future immutable-fact store tomorrow; the path layout and the
EDN shapes are the contract the future store schema must preserve.

```
.agentic-sdk/.spine/
  findings/             ; one .edn per reviewer/lint finding batch (triage consumes)
  triage/
    punch-list.edn      ; the ordered, deduped, numbered findings (triage writes)
    punch-list.md       ; the human-form projection of the above
  run.edn               ; resumption checkpoint (run reads and writes)
  decisions.edn         ; the project's banned-idiom and naming decisions (compile-rules reads)
  escalation.edn        ; collisions and blocked merges (any merge task may write)
  protected-idioms.edn  ; optional vector of idiom strings (triage reads)
```

The EDN shapes below are the working contract. The software port names a
unit (a module or phase id) and gates resumption on the `:plan-hash` of the
plan.

### `findings/*.edn`

One finding map per alert, or a vector of them. The flat shape every
reviewer and `lint` writes:

```edn
{:dimension   :style                   ; one of the active dimensions
 :level       :style                   ; :correctness | :factoring | :style (reporter sets)
 :severity    :high                    ; :high | :medium | :low
 :file        "src/catalog/core.zig"
 :line        42                       ; optional, used for ordering and display
 :evidence    "the matched text"
 :suggestion  "the suggested change, or nil"
 :rule        "rule-id"                ; nil for rule-less opinions (become queries)
 :reporter    "reviewer"}              ; who raised it
```

### `protected-idioms.edn`

A vector of idiom strings. A finding whose `:evidence` contains any of these is
dropped by `triage`. The backstop for author-protected phrasings.

```edn
["functional core" "imperative shell"]
```

### `triage/punch-list.edn`

The output of `triage`:

```edn
{:findings   [{:id           "FINDING-1"
                :dimension    :style
                :level        :style                   ; reporter tier, normalized
                :severity     :high
                :file         "src/catalog/core.zig"
                :line         42
                :evidence     "the matched text"
                :suggestion   "the suggested change"
                :rule         "rule-id"
                :reporters    ["reviewer-a" "reviewer-b"] ; deduped across reporters
                :unit         "catalog"}]              ; module or phase (software port)
  :queries    [{:question  "the rule-less opinion, phrased as a question"
                :dimension :clarity
                :file      "..."
                :line      42
                :reporter  "reviewer-a"}]
  :by-unit    {"catalog"   ["FINDING-1" "FINDING-2"]   ; ids grouped by unit
               "checkout"  ["FINDING-3"]}
  :counts     {:total       3
               :by-level    {:correctness 0, :factoring 1, :style 2}
               :by-severity {:high 2, :medium 1}}}
```

### `run.edn`

The resumption checkpoint `run init` seeds and `run advance`
mutates:

```edn
{:run/scope    {:mode :campaign :units ["catalog" "checkout"]}
  :plan-hash    "sha256:..."              ; gate-arming hash of the plan
  :descriptor-hash "sha256:..."           ; gate-arming hash of the descriptor
 :round        0
 :round-cap    3                         ; max review rounds before dry
 :found-new?   false                     ; did the current round raise new findings
 :stages       {:lint   :pending :review :pending :triage :pending
                :fix    :pending :verify :pending}
 :units        {"catalog"  {:status :draft   :last-commit nil  :open-queries []}
                "checkout" {:status :approved :last-commit "abc123" :open-queries []}}}
```

The `:stages` map uses the fixed stage order `[:lint :review :triage :fix
:verify]`. Each stage is `:pending`, `:done`, or (rarely) `:blocked`. The
directive computation walks this order.

### The `run status` return

Printed by `run status`. This is what the orchestrator reads after each phase,
instead of the transcript:

```edn
{:directive          {:action :run-stage :stage :lint :round 0}
 :round              0
 :round-cap          3
 :collisions-pending 0                       ; count of escalation.edn entries
 :stale-units        ["catalog"]              ; units whose digest drifted post-approval
 :plan-changed?      false                    ; did the plan hash move since init
 :descriptor-changed? false}                  ; did the descriptor hash move since init
```

Directive actions: `:run-stage` (run the named stage at the current round),
`:next-round` (increment the round, reset stages to `:pending`, arm the gate),
`:next-phase` (the rounds for this phase are exhausted; advance to the next
pending phase), `:complete` (the campaign is dry).

### `escalation.edn`

Written by any merge task that hits an ambiguity. The orchestrator reads the
count, never the bodies; a human reads the bodies:

```edn
{:escalations
  [{:type :merge-conflict :unit "catalog" :where "integrate" :source {:branch "fix/catalog"}}
   {:type :decision-proposal :decision "..." :why "..." :date "..." :source {...}}
   {:type :query-proposal   :query "..." :source {...}}
   {:type :stale-candidate  :unit "catalog"}]}
```

### `decisions.edn` (input to `compile-rules`)

The project's non-term style rules:

```edn
{:banned-categories [{:id "em-dash" :pattern "\\u2014" :message "No em-dashes; restructure." :level :warning}
                     "LegacyName"]   ; a bare string bans that word/category
 :naming            {:function "^[a-z][a-z0-9-]*$"}   ; a naming ruling (regex per kind)
 :commit-categories ["Build" "Tests" "Fix" "Refactor" "Docs"]}
```

### The sha256 hand-edit guard

A generated artifact (a rendered punch list, the opencode projection, a
rendered manifest) is stored alongside its sha256. Before the next regeneration
the task compares the current hash to the stored hash: if they differ, someone
hand-edited the generated output, and the task refuses and escalates instead of
clobbering. The opencode projection uses this pattern at its own path. `init!`
(the one-time migration of a hand-written artifact into EDN) is the single
audited exception that parses rendered output.

## Spine-presence levels

Until the future runtime ships, a project sits at one of three levels, recorded
in `:spine :runtime`. The task interface is the same; what differs is which tasks
answer and how.

### Full spine (`:babashka`)

All seven tasks invocable as bb tasks over the EDN working dir. Maximum
guarantees: deterministic triage, conflict-free integration, lossless
resumption, one-way rule projection, zero-token lint, a green runtime port.
The level for Clojure projects and any project with `bb` on PATH. Picks this
level when `:spine :runtime :babashka`.

### Thin spine (`:thin`)

Plain shell-script stand-ins for `lint`, `integrate`, and `run` only. The
rest (detailed `triage`, `compile-rules`) falls back to return-value hand-off:
the orchestrator reads one-line returns from sub-agents and folds them in
conversation, accepting that long campaigns re-derive ground truth from
`git log` and `ls` rather than from a folded EDN store. The level for C, Zig,
and Elixir projects without bb. Picks this level when `:spine :runtime :thin`.

### Return-value-only (`:none`)

No spine tasks. The engine still works: every dispatch returns a contracted
one-line value; the orchestrator threads them; disk holds only durable artifacts
under `.agentic-sdk/artifacts/` and the VCS history. This is the proven
return-value-only mode. Picks this level when `:spine :runtime :none`, or
implicitly when a project opts out of the working dir.

A project picks a level by `:spine :runtime` in the descriptor.
`bootstrap-project` DETECTS the level from `bb` presence on PATH and writes it;
the author may downgrade. The level never upgrades silently.

## Cross-reference

The reference implementation of the adapter is `bb.edn` at the repo root
(authored by another agent). It maps each task name above to a Babashka task
entry that delegates to the namespace owning the invariant. The future runtime
adapter (Phase 5) presents the same task names against the same working-dir
semantics, backed by one static binary and a future store. Skills and agents
import the task names from this contract, not from `bb.edn`.
