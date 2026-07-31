# agentic-sdk system-wide installation: plan

The arc: move all agentic-sdk state out of project repos and into
~/.agentic-sdk/<project-name>/. The project repo stays completely clean.
The SDK is a personal tool, system-installed, shared across projects.

This file is the briefing for implementation. Read it whole before
starting.

## What changed since the last plan

The previous plan (Phases 1 through 7) is DONE. The spine runs under
mino (Phase 2), the store backing works (Phase 3), the self-host flip
landed. All committed.

This plan is a NEW migration: the directory-layout change. It moves
everything from per-project .agentic-sdk/ to a system-wide
~/.agentic-sdk/<project>/. The spine code, seams, and C primitives
from the previous phases carry over unchanged. Only the path
resolution and the bootstrap change.

## Layout

```
~/Code/agentic-sdk/                  # SDK source (shared, single install)
  skills/ agents/ hooks/ src/spine/
  templates/ bin/ docs/

~/.agentic-sdk/<project>/             # per-project state (dir name = key)
  project.edn                         # descriptor (developer-local)
  state/                              # spine working dir
  artifacts/                          # ADRs, decisions, planning docs
  CLAUDE.md                           # router template
  .claude/                            # Claude adapter (generated)
    skills/                           # selected symlinks -> $SDK_SRC/skills/<name>
    agents/                           # selected symlinks -> $SDK_SRC/agents/<name>
    hooks/                            # copied hooks from $SDK_SRC/hooks/
    settings.json                     # generated from descriptor
  .opencode/                          # OpenCode adapter (generated)
    agent/                            # from opencode-sync
    opencode.json
    plugins/

~/Code/<project>/                     # project repo (completely clean)
  (project code only)
  .claude   -> ~/.agentic-sdk/<project>/.claude    (symlink)
  .opencode -> ~/.agentic-sdk/<project>/.opencode  (symlink)
  CLAUDE.md -> ~/.agentic-sdk/<project>/CLAUDE.md  (symlink)
```

Three symlinks in the project root, invisible to git via
.git/info/exclude. That is the entire footprint. No .agentic-sdk/
directory, no .gitignore entries, no committed AI files.

## Resolution

Three values drive everything:

1. SDK source: $AGENTIC_SDK_SRC env var (default: ~/Code/agentic-sdk).
   Set once in shell profile.
2. Project name: basename of the canonical cwd.
   ~/Code/mino gives "mino".
3. Project home: $HOME/.agentic-sdk/<project-name>.

The spine source is never copied. Skills, agents, hooks, and spine
namespaces are served from the SDK source via symlinks or the load
path. Per-project state holds only the descriptor, working state,
artifacts, and generated adapters.

## The agentic CLI

A mino script at $SDK_SRC/bin/agentic (shebang #!/usr/bin/env mino).
Three user-facing commands; the rest are internal plumbing invoked by
the skill system via Bash.

```
# User-facing (human-invoked)
agentic setup                  # create or refresh ~/.agentic-sdk/<project>/
agentic teardown               # remove project symlinks and exclude entries
agentic status                 # print SDK state for cwd

# Internal (skill-invoked via Bash)
agentic triage                 # fold findings into punch list
agentic integrate              # land fix branches oldest-first
agentic lint [--edn PATH] [FILE...]  # mechanical lint
agentic seal [RUN-ID]          # capture a session bundle
agentic rules compile          # project decisions to lint rules
agentic agents sync            # project agent masters to derived format
agentic agents check           # fail when derived agents are stale
agentic resume init [EDN-OPTS] # seed resumption checkpoint
agentic resume status          # print the next directive
agentic resume advance [EDN]   # deep-merge updates into checkpoint
```

Naming rules: single verb for one-of-a-kind actions (triage, lint,
seal, setup, teardown, status). noun verb for grouped operations
(agents sync/check, resume init/status/advance, rules compile). No
overloaded words across categories.

The root is always implicit (resolved from cwd). No [ROOT] argument.

## Enabler: mino *command-line-args*

mino does not expose *command-line-args* in file-dispatch mode. This
is a few-line change in main.c: in the file-dispatch path (around
line 1386), after loading the file, bind a dynamic var
*command-line-args* to a list of the positional args after the
filename.

Without this, the agentic script cannot receive its command argument.
This is the first implementation step.

## Bootstrap (agentic setup)

Run once per project from the project root:

1. Resolve project name from cwd.
2. Create ~/.agentic-sdk/<project>/.
3. Detect project config (languages, VCS, lanes, dimensions) by
   scanning for markers.
4. Write ~/.agentic-sdk/<project>/project.edn.
5. Create ~/.agentic-sdk/<project>/.claude/:
   - skills/: one symlink per selected skill, pointing at $SDK_SRC/skills/<name>.
   - agents/: one symlink per agent, pointing at $SDK_SRC/agents/<name>.
   - hooks/: copy selected hooks from $SDK_SRC/hooks/.
   - settings.json: generated from descriptor :hooks and :permissions.
6. Run opencode-sync to generate ~/.agentic-sdk/<project>/.opencode/.
7. Copy CLAUDE.md from $SDK_SRC/templates/CLAUDE.md.
8. Create three symlinks in project root: .claude, .opencode, CLAUDE.md.
9. Append to .git/info/exclude:
   /.claude
   /.opencode
   /CLAUDE.md

## Hooks

Hook scripts at ~/.agentic-sdk/<project>/.claude/hooks/ reference the
SDK source via $AGENTIC_SDK_SRC to put spine.host on the classpath.
The settings.json hook command stays as
$CLAUDE_PROJECT_DIR/.claude/hooks/<name>.clj, which follows the
symlink. Hooks fire exactly as today.

## What changes

### agentic-sdk

spine.repo: all path resolution shifts from .agentic-sdk/ (project-
relative) to ~/.agentic-sdk/<project>/ (home-relative). A new
project-home function resolves the project name and returns the home
path. working-dir, store-mode-for, descriptor reads, init!, seal!
all use it.

spine.opencode: masters-dir resolves from $SDK_SRC/agents/ (the SDK
source), not a project-local copy.

Hooks: the classpath bootstrap uses $AGENTIC_SDK_SRC instead of
searching for .agentic-sdk/src.

New bin/agentic CLI script (mino, shebang #!/usr/bin/env mino).

bootstrap-project skill: rewritten to write to
~/.agentic-sdk/<project>/, create symlinks, write .git/info/exclude.

templates/gitignore: removed or emptied (nothing to ignore in the
project repo).

Docs (design.md section 10, project-descriptor.md, spine.md):
rewritten for the home-dir model.

### mino

main.c: expose *command-line-args* in file-dispatch mode.

Remove .agentic-sdk/ entirely. Remove .gitignore entries for
.agentic-sdk. Remove spine task entries from mino.edn (spine tasks
invoked via agentic CLI). The project's own mino.edn stays for
build/test tasks, untouched by the SDK.

## Migration (mino)

1. Create ~/.agentic-sdk/mino/.
2. Move .agentic-sdk/{project.edn, state/, artifacts/} to
   ~/.agentic-sdk/mino/.
3. Run agentic setup to generate adapters, hooks, skill symlinks.
4. git rm -r .agentic-sdk/ and commit.
5. Clean .gitignore of .agentic-sdk entries.
6. Remove spine entries from mino.edn.

The project repo goes from having a committed .agentic-sdk/project.edn
and complex .gitignore to having nothing SDK-related at all.

## Critical path

1. mino *command-line-args* (main.c change, rebuild).
2. agentic-sdk project-home resolution.
3. Rewrite spine.repo paths.
4. Write bin/agentic CLI script.
5. Rewrite bootstrap-project (setup command).
6. Migrate mino (move state, clean repo, test).
7. Update docs.

## Key facts and pointers

SDK source: ~/Code/agentic-sdk. Skills at skills/, agents at agents/,
hooks at hooks/, spine at src/spine/. The unified spine.host calls mino
primitives. spine.repo dispatches EDN vs store. Both carry
over unchanged; only path resolution changes.

mino repo: ~/Code/mino. Built via make (C-only). mino.edn has the
project's own tasks. main.c at src/main.c, file dispatch around line
1386. The *command-line-args* change is in the file-dispatch path.

spine.host: uses find-ns for runtime detection, ns-resolve for
capturing functions. No changes needed for the layout migration.

spine.repo: working-dir reads the descriptor to find :spine
:working-dir. The descriptor path changes from
.agentic-sdk/project.edn to ~/.agentic-sdk/<project>/project.edn.
The working-dir changes from .agentic-sdk/state/ to
~/.agentic-sdk/<project>/state/.

Previous work (DONE, committed):
- agentic-sdk jj change zwnozzmm: spine.host + spine.repo seams,
  deterministic EDN serializer, lint portability, store backing,
  descriptor docs.
- mino git commits: run/sha256/realpath/which primitives, mino.edn
  task wiring, project.edn self-host flip, store flip.
