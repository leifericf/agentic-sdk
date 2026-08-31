# agentic-sdk

Architecture of the skill system: runtime-agnostic skills, agents, and a
deterministic spine for software development across a bounded stack.

## 1. Goal and constraints

One skill system, reused across every project, serving a bounded stack
(**C, Zig, Clojure, Elixir**) that shares one architecture
(**Functional Core / Imperative Shell**, with native edges between languages).

Hard constraints:

1. **One neutral home.** This repo (`agentic-sdk`) is the system. Installed
   into a project, it creates a neutral home at `~/.agentic-sdk/<project>/`:
   (skills, agents, hooks, spine, templates), the descriptor, the artifacts
   tree, and the spine working dir. Two thin runtime adapters sit over it:
   `.claude/` as symlink skills/agents/hooks plus a settings hook block;
   `.opencode/` as generated agent projection plus config. Masters never live
   under the adapters.
2. **The deterministic spine is core, always present**, but its implementation
   is an interface: today **mino + EDN**, with the mino store as the target
   immutable-fact store that replaces EDN files. The skill/agent layer talks
   to the spine through **stable task names + a stable working-dir format**,
   never to mino or EDN directly, so the swap is localized.
3. **Descriptor + generated recipes.** `bootstrap-project` detects the stack,
   writes a project descriptor, and materializes concrete `write-<lang>`
   recipes from templates. Maintenance = skeleton + 4 curated recipes +
   detector, not N copies.
4. **Full lifecycle**, but the **human-invoked surface is a handful of deep
   orchestrators** that run big work autonomously between approval gates.
   Everything else is model-invoked.

Design principles:

- **Context is the budget.** Orchestrators read only the one-line returns of
  dispatched sub-agents, never diffs, file bodies, findings dumps, or reasoning.
  Push work down; keep only summaries up.
- **Hand-off is return values, plus disk for the spine.** Sub-agents return one
  contracted line; the spine folds EDN deterministically and is the system of
  record for resumable campaigns.
- **Forward-only DAG.** Work flows one direction; a wrong earlier decision is a
  new forward task plus a decisions entry, not a rewind.
- **Decide and record, never stall.** Agents make the best call, log a
  `DECIDED:` line, and continue. Only the final land waits for the maintainer.
- **No judgment, no model call.** Anything the model would do identically every
  time given the same inputs is promoted to the spine.
- **Tech-stack agnostic at the doctrine layer; concrete at the craft layer.** The
  4 supported languages get real recipes; everything else gets shared doctrine.
- **jj-first VCS.** The descriptor defaults to jj (Jujutsu, git-compatible);
  `write-commit` mandates jj. Existing git repos work via jj's git compatibility,
  and the VCS adapter in the spine detects which is present.

## 2. Architecture overview

```mermaid
flowchart TD
  EP["human surface (§3): deep orchestrators<br/>plan-system · advance-plan · implement-change · audit-code · investigate · fix-bug · ship"]
  MS["meta-skills (§8.3): tailor and extend the system<br/>bootstrap-project · add-language · add-dimension · add-tech"]
  AG["agent fleet (§7): 8 roles<br/>planner · change-runner · review-round-runner · writer · reviewer · editor · verifier (haiku) · ui-designer"]
  SD["shared doctrine (§5): stack-agnostic<br/>orchestration · FC/IS · native-edge · dimensions · craft"]
  LC["language craft (§6): four adapters<br/>write-c · write-zig · write-clj · write-elixir · write-tests · write-ui · write-prose · write-commit · write-changelog"]
  SP["deterministic spine (§4): core, always present<br/>triage · integrate · run · compile-rules · lint · opencode-sync / opencode-check<br/>runtime: mino · store: EDN or mino store"]
  PD["project home (§8): ~/.agentic-sdk/<project>/<br/>vcs · languages · architecture · lanes · dimensions · spine · adr · commit · hooks"]
  EP --> AG
  MS --> AG
  AG --> SD & LC
  SD & LC --> SP
  SP --> PD
```

Cross-cutting, applied across every layer: policy-as-hooks (§9), artifacts and
run-state (§10), and the runtime port (§12).

## 3. The human-invoked surface: few and deep

A small number of deep orchestrators do big work autonomously between approval
gates. Everything else is model-invoked (recipes, primitives), reached through
these seven entry points. Meta-skills (section 8.3) retune the system itself
and are not entry points.

| Entry point | What it does | Approval gates |
|---|---|---|
| `plan-system` | Upstream: turn a problem or idea into an approved backlog and implementation plan. Runs describe-problem to requirements to risks to design-ux (if applicable) to design-technical to backlog to plan, with the Gherkin elicitation gate folded in. | one: the plan/backlog |
| `advance-plan` | **Campaign.** Take a chunk of the plan and build it unattended, phase by phase, dispatching one change-runner per phase through the full implement-review-fix engine. | one up front |
| `implement-change` | **Phase.** Build one change or slice end to end (plan units, tests, impl, integrate, verify, at most 2 review rounds). The lighter entry when there is no campaign. | final land |
| `audit-code` | Review and fix any scope until a round finds nothing new. | final land |
| `investigate` | Ops: triage an incident to root cause and follow-up. | follow-up plan |
| `fix-bug` | Fix one bug: reproduce, failing test, source fix, verify, one commit. | none |
| `ship` | Cut a release end to end. | the tag |

## 4. The deterministic spine

**Core, always present.** Deterministic tasks own the clerical work the model
used to do by hand (triage ordering, parallel-fix integration, resumption
state, rule projection). The model keeps judgment.

### 4.1 The interface (stable; what the skill/agent layer calls)

These names are the contract. The implementation behind them swaps; the calls
do not.

| Task | Reads | Writes | Owns |
|---|---|---|---|
| `triage` | findings dir | `punch-list.edn` and `.md` | dedup, drop protected idioms, order by editing-level then severity, renumber findings |
| `integrate` | fix branches / finding-ids | landed branch and consumed fix branches | cherry-pick parallel fixes oldest-first, report conflicts |
| `run` | scope | directive EDN | compute next directive (run-stage / next-round / next-phase / complete) plus staleness and gate arming |
| `compile-rules` | decisions | lint rules and commit categories | decisions to enforced rules (one-way, deterministic) |
| `lint` | source files | findings EDN | zero-token mechanical pre-pass (style regexes; the AI-tells catalog) |
| `opencode-sync` | ` SDK source agents` masters | `.opencode/agent/` derived | project masters into the OpenCode format |
| `opencode-check` | masters and derived | exit code | fail the lane when the derived form is stale |

### 4.2 The adapter today: one runtime, two stores

The runtime is **mino** (native tasks over EDN files or the mino store). Every
task is a pure function on the unambiguous cases and refuses with a structured
escalation on the ambiguous ones (escalate-don't-guess).

The spine source is a single set of Clojure namespaces. Two seams carry the
remaining variation so the task namespaces never branch:

- **`spine.host`** abstracts filesystem, process, JSON, and hashing. It calls
  mino native primitives (`run`, `sha256`, `realpath`, `which`, `file-exists?`,
  `mkdir-p`, `rm-rf`, `file-seq`) and the built-in `spine.json` for JSON. The
  task namespaces call `host/path`, `host/exists?`, `host/shell`, and so on;
  they never import a runtime namespace directly.

- **`spine.repo`** abstracts the fact store. The EDN backing reads and
  writes files under the working dir. The mino store backing transacts
  datoms with temporal history (`as-of` resume). The task namespaces call
  `repo/read-edn`, `repo/write-edn!`, `repo/read-collection`; the backing
  is chosen from the descriptor's `:spine :store`.

### 4.3 Spine-presence levels

A project sits at one of three spine-presence levels, recorded in the
descriptor:

- **Native spine** (`:mino`): mino tasks over the EDN working dir or the
  mino store. Deterministic triage, conflict-free integration, lossless
  resumption, one-way rule projection, zero-token lint, plus temporal history
  and optional warm-start via SLAD images. The level for projects with
  `mino` on PATH.
- **Thin spine** (`:thin`): plain shell-script stand-ins for `lint`,
  `integrate`, and `run` only; the rest falls back to return-value
  hand-off. For C/Zig/Elixir projects without mino.
- **Return-value-only** (`:none`): no spine tasks. The engine still works;
  long campaigns re-derive ground truth from `git log` and `ls`.

## 5. The shared doctrine layer

Written once, stack-agnostic, the bulk of the system. Lives under
`skills/<recipe>/references/` and a top-level `references/`.

- **`references/orchestration.md`**: context-as-budget, forward-only DAG,
  module-batch fan-out, level-ordered editor waves, autonomy/escalation, the
  phase-exit contract, resumption, runtime adaptation (Agent-tool fan-out vs
  Skill-tool inline).
- **`references/worktree-model.md`**: topology, ordering law (tests before
  implementations; module integration order), conflict law, when to inline vs
  fan out.
- **`references/architecture.md`**: **Functional Core / Imperative Shell** as the
  house pattern; dependency direction (inward); the pure/shell/native three-way
  split; how each of C/Zig/Clojure/Elixir expresses FC/IS; the native boundary
  contract (data in / data out / opaque handles / lifetime discipline). Each
  `write-<lang>` recipe references this instead of restating it.
- **`references/review-model.md`**: the dimension catalog, the level discipline
  (correctness, then factoring, then style; prose: developmental, content, line,
  copy), the finding shape (EDN: dimension, severity, level, file, evidence,
  suggestion), the round cap (2 per phase; `audit-code` uncapped until dry).
- **`references/prose-style.md`** and **`references/style-foundations.md`**: the
  AI-tells catalog, the no-process-ID rule, the commit-message form. Backs
  `write-prose` and `write-commit`, and the `lint` spine task.
- **`references/pyramid.md`**: the test taxonomy. Which surface, which tier,
  "every assertion must be able to fail."

**Authoring discipline.** Every `SKILL.md`, agent `.md`, and reference in this
toolkit follows the prose standard (`references/prose-style.md`): no em-dashes,
no AI tells, no process IDs, terse humanized prose. `write-prose` handles any
prose the system produces; `check-style` enforces it. This doc is no exception.
The public repo never names predecessor or private projects and carries none
of their domain framing.

## 6. The language craft layer

Four thin adapters, each a `write-<lang>` recipe that says "implement FC/IS the
*<lang>* way" plus the language's specific rigor. They share the skeleton:

1. Frontmatter `name: write-<lang>`, `user-invocable: false`.
2. Open with four anchors: the style-standard file, the architecture contract,
   the placement source (module map), and the ADR log ("scan before designing
   against an unexplained rule").
3. Numbered procedure: **place it, then language discipline, then failure model,
   then verify like the lanes**. The discipline slot is the only part that varies.
4. Tests-first; terse comments; the public-text rule.

| Recipe | Discipline slot (what varies) |
|---|---|
| `write-c` | GC ownership up front; error classes; the add-a-primitive / add-a-special-form rituals |
| `write-zig` | Explicit allocator discipline (`defer`/`errdefer`, allocator-per-phase); error unions plus structured diagnostics; no allocation on hot paths |
| `write-clj` | Pure core (data and pure fns) / imperative shell (state, IO) / native wrapper split; bound untrusted seqs before realizing; decode native scalars to domain |
| `write-elixir` | Pure functions in modules / GenServers plus OTP as the shell; supervision trees; NIF boundary discipline (when calling C/Zig) |

Supporting craft recipes (shared, not per-language): `write-tests`, `write-ui`,
`write-prose`, `write-commit`, `write-changelog`. Native-edge doctrine
(C ABI, NIF, a JVM-to-Zig foreign-function edge) lives in
`references/architecture.md`, cited by whichever `write-<lang>` touches the
boundary.

`bootstrap-project` materializes the active `write-<lang>` recipes from
templates, parameterized by `:languages`. The toolkit ships the four curated
masters; the meta-skill copies in only those the project uses.

## 7. The agent fleet

Canonical 8, with documented scale-down configs.

| Agent | Model | Role | Loads |
|---|---|---|---|
| `planner` | inherit | Decompose a chunk into a forward-only DAG; write plan; return summary | `plan-work` |
| `change-runner` | inherit | Run one phase or slice end to end; return one line | `implement-change` |
| `review-round-runner` | sonnet | One review round: lanes, fan-out, triage, editor waves, verify | `run-review-round` |
| `writer` | inherit (+ risk-tier override) | Write one unit (impl or tests) | `write-<lang>` / `write-tests` |
| `reviewer` | sonnet (read-only) | One dimension on one shard; EDN findings or `NO FINDINGS` | a `check-*` |
| `editor` | sonnet | Fix one module's punch-list at one level | `apply-findings` |
| `verifier` | **haiku** | Run deterministic lanes; pass/fail, first error only | `verify-lanes` / `maintain-toolchain` |
| `ui-designer` | inherit | The one specialist: design and review UI (only active when `:ui? true`) | `design-ui` / `check-design` |

Scale-down configs:

- **5-agent** (no campaign, no UI): drop `planner`, `change-runner`,
  `ui-designer`. Planning moves into `implement-change`'s procedure. For
  runtime/library projects without a UI surface.
- **0-agent** (library tier): recipes invoked directly from the root session:
  authoring recipes plus one dimension plus the feedback loop.

Tool split is a hard rule: reviewers are read-only (no Edit/Bash); editors are
the sole source mutators in fix loops; verifier is bash-heavy, no judgment.

## 8. The project descriptor and meta-skills

### 8.1 Descriptor: `~/.agentic-sdk/<project-name>/project.edn`

The single tuning valve, but it lives in the project home, not the project
repo. It is NOT committed to any repo; it is developer-local state.

```edn
{:vcs        :jj                        ; :git | :jj
 :languages  [:clojure :zig]            ; subset of #{:c :zig :clojure :elixir}
 :ui?        true                       ; activates ui-designer + design-ui/check-design
 :architecture
 {:pattern      :functional-core-imperative-shell   ; the house default
  :native-edge? true                                  ; a JVM-to-native bridge, C ABI, NIF
  :modules      {"catalog" "modules/catalog/src"  ...}}
 :lanes
 {:cheap    ["zig fmt --check" "zig build" "zig build test"]
  :wave     ["zig build test -Ddeep" "zig build -Doptimize=ReleaseSafe test"]
  :pre-land ["zig build release-gate"]}
 :dimensions-active #{:style :factoring :correctness :security
                      :performance :memory :conformance}  ; subset of catalog (§11)
 :spine
 {:runtime :mino              ; :mino | :thin | :none
  :store   :edn                ; :edn or :mino (mino store with temporal history)
   :working-dir "state/"}}     ; under the project home
 :adr        {:store "artifacts/adr/" :format :nygard}
 :commit     {:categories ["Build" "Tests" "Fix" "Refactor" "Docs" ...]
              :form "Category: Imperative subject"}
 :hooks      [:format-on-write :deny-secrets]   ; §9
 :permissions {:bash ["git *" "clojure -M:test:*" "zig build:*"]} ; allow-list
 :runtimes   [:claude-code :opencode]}          ; §12, both on default
```

### 8.2 `bootstrap-project` procedure

1. **Detect** the stack: scan cwd for markers (`deps.edn`/`project.clj` for
   Clojure, `build.zig`/`.zig-version` for Zig, `mix.exs` for Elixir,
   `CMakeLists.txt`/`Makefile`/`*.h` for C); detect VCS (`.jj` vs `.git`);
   detect UI surface.
2. **Elicit** the gaps the detector cannot decide (architecture pattern, native
   edge, module map), one batch, at most 3 questions.
3. **Write** `~/.agentic-sdk/<project>/project.edn`.
4. **Run `agentic setup`.** Creates the project home dirs, symlinks `skills/`,
   `agents/`, and `hooks/` from `$SDK_SRC`, generates `.claude/settings.json`
   from the descriptor's `:hooks` and `:permissions`, runs `opencode-sync` to
   derive `.opencode/`, creates the three project-root symlinks
   (`.claude`, `.opencode`, `CLAUDE.md`) into the project home, and writes
   `.git/info/exclude` so the project repo stays clean.
5. **Verify** with `agentic status`.

### 8.3 Meta-skills: extending the catalog

Meta-skills are a distinct class: they **modify the system's own catalog**
(languages, dimensions, lanes, hooks) rather than do project work. Invoked
rarely, when a project hits a gap the curated set does not cover. Each produces
its output **from a skeleton**, so the new addition conforms to the house shape
automatically; each has a **promotion path** from project-local to toolkit
master (§8.4).

| Meta-skill | When invoked | Produces | Skeleton |
|---|---|---|---|
| `bootstrap-project` | once per project, or after a stack change | descriptor plus active recipe subset plus scaffold plus hooks plus CLAUDE.md | the curated masters, filtered by `:languages` |
| `add-language` | a project needs a language outside {C, Zig, Clojure, Elixir}, e.g. Rust, Go, Python, TypeScript | a draft `write-<lang>` recipe wired into `:languages` | the `write-<lang>` skeleton (§6): interview for the discipline slot and failure model, then fill the template |
| `add-dimension` | a project has a bug class the active dimensions miss (the way a memory-unsafe language needs `check-memory`) | a draft `check-<dimension>` wired into `:dimensions-active` | the dimension shape: one-sentence failure model, ordered look-fors, ignore-rules, severity, level |
| `add-tech` | a project adds a non-language concern: a framework, a build target, a spine task, a hook policy | the matching artifact (lane entries, a spine task, a hook template) | the lane/spine/hook schema |

**Procedure shape (shared by all `add-*`):** detect the gap, interview for what
the skeleton's variable slot needs (one batch, at most 3 questions), generate
the artifact against the skeleton, wire it into `project.edn`, validate it
(`add-language` runs a sample write+review; `add-dimension` runs one review
round using it; `add-tech` runs the lane/spine/hook once dry). The new artifact
lands as one commit, category `Skills:`.

### 8.4 Promotion path

A project-local addition authored by an `add-*` meta-skill is **project-local
first**: it lives in the project's ` SDK source skills/`, snapshotted, owned
by the project. If it proves generally useful, it is promoted to a toolkit
master via the same discipline that promotes captured guidance: `incorporate-feedback`
classifies it, and a `Skills: Promote <name> to toolkit master` commit moves
the refined version into the toolkit's curated set. This is the single reuse
valve: every `add-*` output is a candidate for promotion, and promotion is
deliberate, never automatic.

## 9. Policy-as-hooks and the standard router

Policy that lives in **hooks**, not in prompts, is far more reliable. The
toolkit ships hook **templates** activated by the descriptor:

- **`format-on-write`** (PreToolUse/PostToolUse on Write|Edit): runs the
  project's formatter at the editor boundary. `check-format` becomes free.
- **`deny-secrets`** (PreToolUse on Read|Edit|Write): blocks `.env`,
  credentials, private keys.
- **`require-tests-before-land`** (PreToolUse on commit/merge): gates land on a
  green lane run.

`bootstrap-project` symlinks the project root `CLAUDE.md` to
` SDK source templates/CLAUDE.md`: a standard, hard-rule-first router
identical across projects. It carries no per-project content (the
descriptor and `artifacts/` hold the specifics), so it is regenerated, not
committed.

## 10. Artifacts and run-state layout

```
~/.agentic-sdk/<project>/              # project home (per-project state, NOT in any repo)
  project.edn                          # the descriptor (developer-local)
  artifacts/                           # durable
    planning/   product-backlog.md, etc.
    decisions/  decision-log.md, open-questions.md
    project/    project-meta.md
    ops/        incident-*.md, rca-*.md
    adr/        NN-slug.md
  CLAUDE.md                            # router template (copied from $SDK_SRC/templates/)
  .claude/                             # Claude Code adapter
    skills/                            # symlinks to $SDK_SRC/skills/<name>
    agents/                            # symlinks to $SDK_SRC/agents/<name>.md
    hooks/                             # copied hooks
    settings.json                      # generated from descriptor :hooks + :permissions
  .opencode/                           # OpenCode adapter
    agent/                             # derived by opencode-sync
    opencode.json
  state/                               # spine working dir
    findings/ triage/ run.edn decisions.edn escalation.edn
    store.db                           # mino store file (when :store :mino)
  runs/<slug>/                         # ephemeral campaign state
    plan.edn

~/Code/<project>/                      # project repo (completely clean)
  (project code only)
  .claude   -> ~/.agentic-sdk/<project>/.claude    (symlink, hidden via .git/info/exclude)
  .opencode -> ~/.agentic-sdk/<project>/.opencode  (symlink, hidden via .git/info/exclude)
  CLAUDE.md -> ~/.agentic-sdk/<project>/CLAUDE.md  (symlink, hidden via .git/info/exclude)
```

Commit policy: project repos commit NOTHING SDK-related. The three project-root
symlinks (`.claude`, `.opencode`, `CLAUDE.md`) are hidden via `.git/info/exclude`,
not `.gitignore`, so the project repo stays completely clean with no AI or LLM
files and no `.gitignore` AI entries. The project home at
`~/.agentic-sdk/<project>/` is developer-local state, not version controlled.
The SDK source is shared system-wide from one location (typically
`~/Code/agentic-sdk`), referenced by symlinks rather than copied into each
project.

Resume model: the orchestrator reads `run` (not the transcript) after each
phase; workers return pointer lines; sub-orchestrators return one line. Disk is
the system of record, so compaction is lossless.

## 11. The review dimensions: a catalog, not a fixed set

A catalog of ~10, each a `check-<dimension>` primitive with the same shape
(frontmatter, one-sentence failure model, ordered look-fors, ignore-rules,
severity, level). A project's `:dimensions-active` selects the subset.

| Dimension | Looks for |
|---|---|
| `check-correctness` | logic bugs, nil/empty/boundary, arithmetic |
| `check-factoring` | module boundaries, dependency direction, duplication |
| `check-style` | naming, idiom, comment debt, AI-tells |
| `check-conformance` | behavior matches dossier/ADRs |
| `check-security` | untrusted-input to unsafety, traversal, bypass |
| `check-performance` | hot-path allocation, budget breaks |
| `check-portability` | platform branches, endianness, FS semantics |
| `check-memory` | ownership, lifetimes, leaks, GC safety (C/Zig) |
| `check-design` | design language, view-spec cleanliness (UI only) |
| `check-clarity` | (prose/docs) reader experience, jargon, pacing |

The reviewer agent's dimension allowlist is the floor per module type; the
descriptor tunes it. A C/Zig project activates `:memory`; a UI project activates
`:design`; a library project may run `:style` alone.

## 12. Portability across runtimes (Claude Code and OpenCode)

**Claude Code is the master format.** The spine task `opencode-sync` projects
the masters (agents first, then the skill index) into `.opencode/`, so one
system drives both runtimes. The verify lane `opencode-check` fails pre-land
if the derived artifacts are stale against the masters; running
`opencode-sync` fixes it. One source of truth, two runtimes, no manual
double-editing.

Two consequences:

- **The port is a spine task, not a skill.** It runs in the same runtime the
  spine adapter selects (mino), so it stays
  installable across all four language stacks. The descriptor records nothing
  extra: the port is always on for every runtime in `:runtimes`.
- **Masters are never hand-edited in the derived form.** `.opencode/` is
  generated and gitignored or regenerated; edits go to the masters at
  ` SDK source agents/*.md` and ` SDK source skills/`, then re-projected.
  This is the same "code never parses its own rendered output" discipline
  applied to generated artifacts.

## 13. Skill inventory and streamlining

The system is factored into atomic skills with one unambiguous layer each,
recomposed by the entry points. This section is the authoritative inventory:
prefix discipline, the atoms by layer, and the recomposition.

### 13.1 Prefix and layer discipline

One prefix per layer:

| Affix | Layer | Run by |
|---|---|---|
| bare verb | entry point | maintainer |
| (meta) | meta-skill | maintainer, rarely |
| `write-<surface>` | authoring recipe | writer |
| `run-<procedure>` | procedure recipe | review-round-runner |
| planning verb (`describe-`/`define-`/`design-`/`review-`/`create-`/`pick-`/`plan-`) | planning recipe | plan-system |
| `assess-<concern>` | planning dimension (fan-out) | plan-feature |
| `check-<dimension>` | review dimension (read-only judgment) | reviewer |
| `verify-lanes` | deterministic lanes (no judgment) | verifier |

Two hard rules: **`check-` is reserved for review dimensions only** (model
judgment, read-only reviewer); deterministic gates are `verify-lanes`, never
`check-`. **Planning concerns are `assess-`**; release and incident concerns are
recipes under `ship` and `investigate`, not `assess-`. Every skill carries a
Boundaries section naming its owning sibling and a one-line return contract.

### 13.2 The atomic inventory

Every atom is non-overlapping and single-layer.

- **Entry points (7):** `plan-system`, `advance-plan`, `implement-change`,
  `audit-code`, `investigate`, `fix-bug`, `ship`.
- **Meta-skills (4):** `bootstrap-project`, `add-language`, `add-dimension`,
  `add-tech`.
- **Planning recipes (8):** `describe-problem`, `define-requirements`,
  `review-risks`, `design-ux` (UI only), `design-technical`, `create-backlog`,
  `pick-feature`, `plan-feature`.
- **Planning dimensions (4):** `assess-observability`, `assess-testing`,
  `assess-data`, `assess-rollout`.
- **Authoring recipes (9):** `write-c`, `write-zig`, `write-clj`, `write-elixir`,
  `write-tests`, `write-ui`, `write-prose`, `write-commit`, `write-changelog`.
- **Procedure recipes (2):** `run-review-round`, `run-spike`.
- **Review dimensions (10):** `check-correctness`, `check-factoring`,
  `check-style`, `check-conformance`, `check-security`, `check-performance`,
  `check-portability`, `check-memory`, `check-design`, `check-clarity`.
- **Conformance recipes (2):** `extend-conformance-corpus`,
  `triage-conformance-diffs`. The differential-probe pair: grow the corpus
  (a `write-tests` specialization), then classify what the probe surfaces
  (`check-conformance` applied to diff output instead of source).
- **Ops and release recipes (5):** `analyze-root-cause`, `assess-risk`,
  `design-ui`, `review-incident`, `triage-logs`. `design-ui` is the UI design
  recipe the `ui-designer` loads; `assess-risk` is the release-risk recipe
  under `ship`; `analyze-root-cause`, `review-incident`, `triage-logs` are ops
  recipes under `investigate`.
- **Other primitives (9):** `verify-lanes`, `apply-findings`,
  `gather-module-context`, `maintain-toolchain`, `record-decision`,
  `capture-guidance`, `incorporate-feedback`, `develop-at-repl`,
  `vertical-slice-postmortem`.

### 13.3 Recomposition

Entry points compose the atoms; atoms never call entry points.

| Entry point | Composes |
|---|---|
| `plan-system` | `describe-problem`, `define-requirements`, `review-risks`, `design-ux` (if UI), `design-technical`, `create-backlog`, `pick-feature`, `plan-feature`; the `assess-*` fan out inside `plan-feature` |
| `advance-plan` | `planner` running `plan-work`, then one `change-runner` per phase |
| `implement-change` | `writer` (`write-<lang>` / `write-tests`), `verifier` (`verify-lanes`), `review-round-runner` (`run-review-round` fanning out `check-*` and fixing via `apply-findings`) |
| `audit-code` | `run-review-round` (`check-*` fan-out, spine `triage`, `apply-findings`) until a round finds nothing new |
| `fix-bug` | reproduce, `write-tests`, `write-<lang>`, `verify-lanes` |
| `ship` | `verify-lanes` (pre-land), `write-changelog`, tag |
| `investigate` | `triage-logs`, `analyze-root-cause`, `review-incident` |
