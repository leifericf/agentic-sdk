# Project descriptor: `~/.agentic-sdk/<project>/project.edn`

Status: **Authoritative schema.** The descriptor is the single tuning valve for a
project. Recipes read it to pick a language recipe; the spine reads it to pick a
runtime and a store; hooks read it to arm policies; orchestrators read it to know
which lanes to run and which dimensions to fan out. Nothing downstream hardcodes
a stack. `bootstrap-project` writes it once; the `add-*` meta-skills amend it.

The file lives at `~/.agentic-sdk/<project>/project.edn`, developer-local and not
committed to any repo. EDN, a single map, no code execution. Every field has a
default, so a minimal descriptor is `{}`. Every field is optional in that the
system degrades to a sane default, but a production project states the ones
that matter.

Each field below carries: **type**, allowed **values**, **default**, what it
**configures**, who **reads** it (skill, agent, hook, or spine task), and its
**origin** under `bootstrap-project` (DETECT resolves it automatically; ELICIT
asks the user, default applied when the user gives no answer).

## Field reference

### `:vcs`

- **type:** keyword.
- **values:** `:git` or `:jj`.
- **default:** `:jj`.
- **configures:** which VCS commands the spine and `write-commit` emit. The VCS
  adapter detects which is present at runtime regardless, so this field records
  the project's intended primary.
- **reads:** `write-commit`, the `integrate` spine task, `ship`.
- **origin:** DETECT. A `.jj/` dir resolves to `:jj`; `.git/` alone resolves to
  `:git`. jj's git compatibility means a jj-first repo on a git host still reads
  `:jj`.

### `:languages`

- **type:** vector of keywords.
- **values:** any subset of `#{:c :zig :clojure :elixir}`. Order is conventional
  (consumer language last), not enforced; it only orders the materialized recipe
  list.
- **default:** `[]` (no recognized language markers).
- **configures:** which `write-<lang>` recipes `bootstrap-project` materializes
  into `~/.agentic-sdk/<project>/skills/`, and which language discipline the `writer` agent loads
  per unit.
- **reads:** `bootstrap-project`, `writer`, `change-runner`.
- **origin:** DETECT. Markers: `deps.edn` or `project.clj` for `:clojure`;
  `build.zig` or `.zig-version` for `:zig`; `mix.exs` for `:elixir`;
  `CMakeLists.txt`, `Makefile`, or `*.h` alongside `*.c` for `:c`. A project
  outside the bounded stack lands `[]` and gets shared doctrine only.

### `:ui?`

- **type:** boolean.
- **values:** `true` or `false`.
- **default:** `false`.
- **configures:** whether the `ui-designer` agent joins the fleet and whether
  `design-ui` and `check-design` are loadable recipes. When `false`, the UI
  dimension and agent are absent from every fan-out.
- **reads:** fleet composition in `change-runner` and `review-round-runner`;
  `:dimensions-active` validation (the `:design` dimension requires `:ui? true`).
- **origin:** DETECT with an ELICIT fallback. Heuristics look for a frontend
  surface (a `src/` with view files, a framework marker); if undetermined, the
  one batch question includes it.

### `:architecture`

A map with three keys.

#### `:architecture :pattern`

- **type:** keyword.
- **values:** `:functional-core-imperative-shell` is the house default and the
  only curated pattern. A project may state a custom keyword; the `write-<lang>`
  recipes then reference `references/architecture.md` for the FC/IS doctrine and
  the project owns any divergence.
- **default:** `:functional-core-imperative-shell`.
- **configures:** which architecture reference the `writer` anchors to, and how
  `check-factoring` judges module boundaries (pure core, imperative shell,
  native edge).
- **reads:** `write-<lang>` recipes, `check-factoring`, `check-conformance`.
- **origin:** ELICIT, default applied. The detector does not guess architecture.

#### `:architecture :native-edge?`

- **type:** boolean.
- **values:** `true` or `false`.
- **default:** `false`; `true` is suggested when two or more languages are
  present and at least one pair shares a native boundary (a JVM-to-native
  bridge, C ABI, NIF).
- **configures:** whether the native-edge doctrine section of
  `references/architecture.md` is cited by the touching `write-<lang>` recipe,
  and whether `check-conformance` checks boundary contracts.
- **reads:** `write-<lang>` recipes at the edge, `check-conformance`.
- **origin:** ELICIT. The detector suggests `true` when `:languages` has more
  than one entry, but the author confirms.

#### `:architecture :modules`

- **type:** map of module name (string) to source root (string path).
- **values:** any map. Example: `{"catalog" "modules/catalog/src",
  "checkout" "apps/checkout/src"}`.
- **default:** `{}` (the project is treated as a single module).
- **configures:** the placement source for `write-<lang>` (where new code goes),
  the unit boundaries for fan-out in `change-runner`, and the per-module grouping
  in `review-round-runner`.
- **reads:** `write-<lang>` recipes, `change-runner`, `review-round-runner`,
  `check-factoring`.
- **origin:** ELICIT, with a detector hint. The detector proposes module roots
  from the directory structure; the author confirms or corrects in the one batch.

### `:lanes`

A map of three keys, each a vector of shell command strings. Commands run in the
project root, in vector order, and stop on the first failure.

#### `:lanes :cheap`

- **type:** vector of strings.
- **values:** any shell commands.
- **default:** per-language defaults the detector materializes (Clojure
  `["mino task test"]`, Zig `["zig fmt --check" "zig build"]`).
- **configures:** the lanes the `verifier` agent runs on every unit and after
  every edit. The cheap tier fits in a tight loop.
- **reads:** `verifier` (running `verify-lanes`), the `format-on-write` and
  `require-tests-before-land` hooks.
- **origin:** DETECT per language template, refined by ELICIT.

#### `:lanes :wave`

- **type:** vector of strings.
- **values:** any shell commands.
- **default:** per-language deeper lanes (Zig `["zig build test -Ddeep",
  "zig build -Doptimize=ReleaseSafe test"]`).
- **configures:** the lanes `review-round-runner` runs once per review round. The
  slower tier that justifies a wave, not a tight loop.
- **reads:** `review-round-runner`, `verifier`.
- **origin:** DETECT, refined by ELICIT.

#### `:lanes :pre-land`

- **type:** vector of strings.
- **values:** any shell commands.
- **default:** per-language release-gate lane (Zig `["zig build release-gate"]`).
- **configures:** the gate that must pass before a land. The
  `require-tests-before-land` hook arms on this; `ship` runs it before the tag.
- **reads:** `ship`, the `require-tests-before-land` hook, `verifier`.
- **origin:** DETECT, refined by ELICIT.

### `:dimensions-active`

- **type:** set of keywords.
- **values:** any subset of the section-11 catalog:
  - `:correctness` logic bugs, nil and empty and boundary, arithmetic.
  - `:factoring` module boundaries, dependency direction, duplication.
  - `:style` naming, idiom, comment debt, AI tells.
  - `:conformance` behavior matches the dossier and the ADRs.
  - `:security` untrusted input to unsafety, traversal, bypass.
  - `:performance` hot-path allocation, budget breaks.
  - `:portability` platform branches, endianness, filesystem semantics.
  - `:memory` ownership, lifetimes, leaks, GC safety (C and Zig).
  - `:design` design language, view-spec cleanliness (UI only, requires
    `:ui? true`).
  - `:clarity` reader experience, jargon, pacing (prose and docs).
- **default:** a per-stack floor the detector materializes.
  `#{:correctness :factoring :style :conformance}` for library projects; adds
  `:security` and `:performance` for application projects; adds `:memory` for C
  and Zig; adds `:design` when `:ui? true`; adds `:clarity` for prose-heavy docs.
- **configures:** the reviewer agent's dimension allowlist per fan-out. A
  dimension not in this set is never dispatched.
- **reads:** `review-round-runner`, `reviewer`, `audit-code`.
- **origin:** DETECT (the floor), refined by ELICIT (the project may drop or
  add).

### `:spine`

A map of three keys.

#### `:spine :runtime`

- **type:** keyword.
- **values:** `:mino`, `:thin`, or `:none`. See `spine.md` for what
  each level provides. `:mino` runs the spine under the mino runtime.
- **default:** `:mino` when `mino` is on the project PATH; `:thin` otherwise
  (C, Zig, or Elixir without mino); `:none` only when the project opts out
  explicitly.
- **configures:** which spine tasks are invocable and how (mino tasks versus
  shell stand-ins versus return-value hand-off).
- **reads:** every spine task dispatch, the `bootstrap-project` scaffold step.
- **origin:** DETECT. `mino` presence on PATH decides; the author may downgrade.

#### `:spine :store`

- **type:** keyword.
- **values:** `:edn` (today) or `:mino` (mino store with temporal history).
- **default:** `:edn`.
- **configures:** the on-disk format of the working dir and the ledger. The task
  interface is identical across stores; only the serialization changes.
- **reads:** the spine adapter (the serialization layer).
- **origin:** DETECT. `:edn` today; `:mino` when the mino store backing ships.

#### `:spine :working-dir`

- **type:** string path.
- **values:** any path relative to the project home. Convention: `state/`.
- **default:** `state/`.
- **configures:** where the spine writes proposals, scans, findings, triage
  output, run state, and escalations. Developer-local state, never committed.
- **reads:** every spine task (each resolves this once and prefixes all paths).
- **origin:** DETECT, fixed to the canonical `state/` home.

### `:adr`

A map of two keys.

#### `:adr :store`

- **type:** string path.
- **values:** any directory path.
- **default:** `"artifacts/adr/"`. Set to `"docs/adr/"` when the project prefers
  to keep ADRs with committed docs in the project repo rather than under the SDK
  home.
- **configures:** where `record-decision` writes ADR files and where
  `write-<lang>` looks for prior decisions ("scan before designing against an
  unexplained rule").
- **reads:** `record-decision`, `write-<lang>` recipes, `check-conformance`.
- **origin:** ELICIT, default applied.

#### `:adr :format`

- **type:** keyword.
- **values:** `:nygard` (the Michael Nygard one-pager template). Other formats
  may be added; only `:nygard` is curated.
- **default:** `:nygard`.
- **configures:** the template `record-decision` fills.
- **reads:** `record-decision`.
- **origin:** ELICIT, default applied.

### `:commit`

A map of two keys.

#### `:commit :categories`

- **type:** vector of strings.
- **values:** any. Convention: short capitalized nouns scannable in a log.
- **default:** `["Build" "Tests" "Fix" "Refactor" "Docs" "CI" "Skills"
  "Scaffold" "Serve" "Plans"]`.
- **configures:** the allowed leading category for `write-commit`. A commit whose
  category is not in this set fails the commit lint.
- **reads:** `write-commit`, the commit-message hook.
- **origin:** ELICIT, default applied.

#### `:commit :form`

- **type:** string template.
- **values:** any. The words `Category` and `Imperative subject` are the
  conventional placeholders.
- **default:** `"Category: Imperative subject"`.
- **configures:** the enforced single-line shape of every commit. Drives the lint
  the commit hook runs.
- **reads:** `write-commit`, the commit-message hook.
- **origin:** ELICIT, default applied.

### `:hooks`

- **type:** vector of hook template keys (keywords).
- **values:** any subset of `#{:format-on-write :deny-secrets
  :require-tests-before-land}`.
  - `:format-on-write` runs the project formatter at the editor boundary
    (PreToolUse and PostToolUse on Write and Edit).
  - `:deny-secrets` blocks `.env`, credentials, and private keys (PreToolUse on
    Read, Edit, Write).
  - `:require-tests-before-land` gates land on a green `:pre-land` lane run
    (PreToolUse on commit and merge).
- **default:** `[:format-on-write :deny-secrets]`.
- **configures:** which hook templates `bootstrap-project` scaffolds into
  `~/.agentic-sdk/<project>/hooks/`.
- **reads:** the host runtime hook loader, `bootstrap-project`.
- **origin:** ELICIT, default applied.

### `:permissions`

- **type:** map of tool keyword to a vector of allow patterns (strings).
- **values:** today `:bash` carries the bash allow-list, for example
  `["git status:*" "clojure -M:test:*" "zig fmt:*"]`.
- **default:** `{}` (no extra allowances; the host runtime defaults apply).
- **configures:** the permission allow-list `bootstrap-project` writes into
  `.claude/settings.json` and `.opencode/opencode.json`. It is the source of
  truth for the allow-list, so those adapter files are regenerated and
  gitignored rather than committed.
- **reads:** `bootstrap-project`.
- **origin:** ELICIT (one batch) or DETECT from `:lanes`.

### `:runtimes`

- **type:** vector of keywords.
- **values:** any subset of `#{:claude-code :opencode}`. `:claude-code` is the
  master format; `:opencode` is the derived projection.
- **default:** `[:claude-code :opencode]`.
- **configures:** which runtime projections the `opencode-sync` spine task
  maintains. The port is always on for every runtime listed; adding a runtime
  adds a projection to keep green.
- **reads:** `opencode-sync`, `opencode-check`, `bootstrap-project`.
- **origin:** ELICIT, default applied. Both on by default; a project may drop
  `:opencode` if it runs Claude Code only.

## Full annotated example

Lives at `~/.agentic-sdk/<project>/project.edn`, developer-local and not committed.

```edn
{:vcs        :jj                                  ; :git | :jj  (default :jj)
 :languages  [:zig :clojure]                      ; subset of #{:c :zig :clojure :elixir}
 :ui?        true                                 ; activates ui-designer + design-ui/check-design
 :architecture
 {:pattern      :functional-core-imperative-shell ; the house default
   :native-edge? true                               ; a JVM-to-native boundary present
  :modules      {"catalog"  "modules/catalog/src"
                 "checkout" "apps/checkout/src"}}
 :lanes
 {:cheap    ["zig fmt --check" "zig build" "mino task test"]
  :wave     ["zig build test -Ddeep"
             "zig build -Doptimize=ReleaseSafe test"
             "mino task test :integration"]
  :pre-land ["zig build release-gate" "mino task test :full"]}
 :dimensions-active #{:style :factoring :correctness :security
                      :performance :memory :conformance :design}
 :spine
   {:runtime     :mino                               ; :mino | :thin | :none
    :store       :edn                               ; :edn | :mino (mino store with temporal history)
     :working-dir "state/"}
  :adr        {:store "artifacts/adr/" :format :nygard}
 :commit     {:categories ["Build" "Tests" "Fix" "Refactor" "Docs" "CI" "Skills"]
              :form      "Category: Imperative subject"}
 :hooks      [:format-on-write :deny-secrets :require-tests-before-land]
 :permissions {:bash ["git *" "clojure -M:test:*" "zig build:*"]}
 :runtimes   [:claude-code :opencode]}
```

## Detection versus elicitation

`bootstrap-project` step 1 (DETECT) resolves these without asking:

| Field | Signal |
|---|---|
| `:vcs` | `.jj/` present, else `.git/` |
| `:languages` | file markers per language |
| `:ui?` | frontend surface markers (heuristic) |
| `:spine :runtime` | `mino` on PATH |
| `:spine :store` | always `:edn` today |
| `:spine :working-dir` | canonical `state/` |
| `:lanes` | per-language template, then author refines |
| `:dimensions-active` | per-stack floor, then author refines |

Step 2 (ELICIT) asks one batch, at most three questions, for the gaps the
detector cannot decide:

| Field | Question shape |
|---|---|
| `:architecture :pattern` | confirm FC/IS or name a divergence |
| `:architecture :native-edge?` | is there a native boundary between the languages |
| `:architecture :modules` | name the module roots (detector proposes) |
| `:adr :store`, `:adr :format` | confirm defaults |
| `:commit :categories`, `:commit :form` | confirm defaults |
| `:hooks` | which policies to arm |
| `:permissions` | the bash allow-list (or DETECT from `:lanes`) |
| `:runtimes` | confirm both on |

## Closing note

The single tuning valve. A field change here is the only place a project
retunes the system: add a language, drop a dimension, arm a hook, downgrade the
spine. Every `add-*` meta-skill amends this file and only this file. The
descriptor is not version controlled; it is developer-local state at
`~/.agentic-sdk/<project>/project.edn`.
