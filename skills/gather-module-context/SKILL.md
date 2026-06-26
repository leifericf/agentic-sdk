---
name: gather-module-context
description: Produce a compact module brief for a dispatched agent, built from disk, returned as the final message, never written to disk.
user-invocable: false
---

# gather-module-context

Produce the few hundred tokens a dispatched agent needs instead of
rediscovering the module. Build from disk, not memory. The orchestrator
pastes the return value into its dispatch prompts. Nothing is written
to disk.

## Procedure

Sources, in order:

1. **The module map.** The descriptor's `:architecture :modules` row
   for the module: its responsibility, the files it owns, the
   dependency directions that touch it. Detailed contracts live in
   `references/architecture.md`. Pre-build, the row is the module
   definition until code lands; mark `RECENT: pre-build, no history
   yet` and `TESTS: pre-build, no tests yet`.
2. **`ls <module-dir>`.** The actual files, if the directory exists.
   The map can lag the code; note any drift. If the
   directory does not exist yet, skip this source and note
   `SIZES: pre-build, dir not yet created`.
3. **Owning tests.** The test namespace or file that exercises the
   module, and the round-trip integration test if it sits on the native
   edge. Pre-build: empty; say so.
4. **`jj log` over the module path** (or `jj log -r` limited to recent
   commits touching the dir). What changed recently and why; flags
   active work and fresh bug history. Pre-build: empty; say
   `no history yet`.
5. **`docs/adr/README.md`.** The decision index, if it exists (it is
   created by `record-decision` when the first ADR lands; do not create
   it here). Quote any row whose title touches the module or its
   idioms, so dispatched agents know what is deliberate before they
   review or edit.
6. **Sizes.** `wc -l` on existing files to surface any approaching the
   soft limits (about 800 lines per file, about 200 per function). A
   file near the limit is a factoring finding waiting to happen.
   Pre-build: nothing to measure.

Brief format (keep under about 40 lines):

```
MODULE <dir>
RESPONSIBILITY: <one line per file, from the map and the code>
BOUNDARY: may include <...>; must NOT touch <other modules, the pure core from the shell, the public API from non-owning modules>
TESTS: <test files> (run: the owning lane)
RECENT: <3-5 one-line commits, or "no history yet">
SIZES: <any file or function near soft limits>
NOTES: <gotchas: native handle lifetimes, store transaction shapes, real-time constraints, validation layers, platform splits, generated files>
```

## Boundaries

Owns the read-only module brief. Module placement and language
discipline are `write-<lang>`; the dispatch that consumes the brief is
the orchestrator (`implement-change`, `run-review-round`). Never
edits source.

## Return

The brief, as the final message. Never written to disk.
