# Worktree model

Topology, ordering, and conflict law for parallel writer and editor
agents in `implement-change` and `audit-code`. Most runs skip it: read
"When to inline" and use inline mode unless worktree mode earns its
complexity.

## Topology

```
current tip  (the working-copy parent; the integration target,
 |            named by its description, not a branch)
 |
 |-- test units      (the change's test files; fold in first)
 |-- impl units      (the change's implementation files)
 |
 `-- editor fixes    (applied during review rounds)
```

Work is a linear stack of commits on top of the current tip. The
stack head is the integration target, named by its description, not a
branch. Branches or bookmarks mark main, the push anchors, and major
deliverables; they are never dev branches, and work never bases on the
protected main line (basing on main when main lags the tip forks
history). Every writer and editor sub-unit is a change that folds into
the stack (amended into its task commit) or restacks on it. The stack
stays linear throughout; main advances over it only at land time, by
the maintainer.

## Ordering law

1. **Test files land before their implementations.** The integrate
   order proves fail, then pass; an implementation that lands without
   its test in history has lost the proof.
2. **Modules integrate in dependency order.** Leaf modules (zero
   dependencies on other modules) first, then the modules that depend
   on them, then the app or top-level composition that wires them. A
   pure-core module that nothing else depends on is the canonical leaf:
   nothing in the integrate order waits on anything else. Dependency
   direction runs inward, toward the core (see `architecture.md`).
3. **One module per sub-unit.** A writer who needs another module
   returns `needs-cross-module <reason>` and the orchestrator
   dispatches a cross-module unit after the module waves finish.
4. **Levels never mix in one editor wave.** Correctness first, then
   factoring, then style. A factoring fix in the same wave as a
   correctness fix obscures the correctness diff. The level taxonomy
   and the reason the order is fixed live in `review-model.md`.

## Conflict law

- Two writers in different modules do not conflict (that is the point
  of the module-shard split).
- Two writers in the same module MUST be serialized (dispatch in
  sequence, not parallel). Wanting two writers in the same module
  means the units were not split by module.
- A leaf module with zero dependencies on other modules is a special
  case: any change to it can land first and parallel to any other
  module's work.
- Editors in a review round never conflict with each other on source
  (they edit different modules), but they may conflict on test files
  shared across modules. Integrate tests in their own sub-wave before
  editors' source changes.

## When to inline

Use inline mode (single session, working in the current change, no
separate workspaces) when:

- The change touches one or two modules. Two parallel writers do not
  pay for the workspace ceremony.
- The change is mostly test additions or ADR or doc work, not
  fan-out-able source.
- You are early in the project and the modules are still churning:
  parallel work will conflict more than it parallelizes.
- The maintainer is reviewing each step interactively anyway.

Inline mode keeps the discipline: test-first ordering, level-ordered
editor waves, verify-then-land. The only thing it gives up is
wall-clock parallelism.

## When to fan out

Use worktree mode when:

- The change touches three or more independent modules and the unit
  work is genuinely independent (the module-shard briefs confirm no
  shared source).
- A review round has findings in four or more modules and each
  editor's fix is local to its module.
- Wall-clock matters (a release is blocked on the change landing).

Worktree mode dispatch:

Where the runtime supports it, the orchestrator dispatches the agent
with workspace isolation, and the runtime gives that subagent its own
workspace on the stack head. The orchestrator does not create
workspaces by hand in the common path. The agent commits in that
workspace and returns its result (its `LANDED <change-id>` line and
any `CHANGELOG:` lines) in its final message; the orchestrator folds
the change into the stack (amend into the task commit) or restacks it
onto the stack head. Run the writer inline in the current change when
a separate workspace is not worth it.

The dispatch prompt carries the unit's complete spec and module brief,
so the writer never needs this session's context; the return is the
hand-off back.

## Resume after crash

The hand-off between agents is return values held in the orchestrator's
context, never files. The only optional disk state is a minimal,
gitignored resume checkpoint under `~/.agentic-sdk/<project>/runs/<slug>/`: what is
done (units landed, rounds completed, findings still open), so a crash
or token-exhausted run can pick up. It is never committed, never the
hand-off medium; a clean run can delete it. Do not create findings,
proposals, or context subdirs.

On resume:

1. The log confirms the current change is the stack head on the tip.
2. The checkpoint, if present, shows how far the run got: units
   landed, the last completed round, findings still open.
3. The next round is the last completed round plus one; re-run any
   round that was in flight when the crash happened.

The checkpoint records progress only; the working data lived in the
agents' contexts and is regenerated by re-running the interrupted wave
or round.
