---
name: write-commit
description: Recipe for any commit. Mandates jj over git, the single-line category-first message, and the jj workflow. Invoke before any commit, by any agent or the maintainer.
user-invocable: false
---

# write-commit

Invoke before any commit. The project uses jj (Jujutsu) as the VCS,
colocated with git for remote compatibility. jj owns the change model;
never commit with raw git.

For the prose craft the subject rides on (active voice, concrete over
general, no AI tells), invoke write-prose first. This skill owns the
form: the jj workflow, the single-line category-first subject, the
categories, the pre-flight check.

## jj, not git

The repo has both `.git/` and `.jj/`. jj is the source of truth for the
working change; git is the wire format for the remote.

Use:

- `jj st` (status), `jj log --limit N` (history), `jj diff -r @` (the
  current change).
- `jj describe -m "..."` to set the message on the current change.
- `jj new` for a fresh empty change on top; `jj squash` to fold into the
  parent; `jj unsquash` to split it back out.
- `jj rebase -r <rev> -d <dest>` to rebase onto another change.
- `jj git push` to push (not `git push`).

Do not use:

- `git commit` (bypasses jj's change model). Use `jj describe` then
  `jj new`.
- `git add` / `git rm` (jj auto-tracks working-copy mutations; no
  staging step).
- `git stash` (no jj analogue). Use `jj new` to start a fresh change;
  the current one is preserved.
- `git rebase` (use jj's, which understands colocation).
- `git am` / `git apply` (use `jj squash` or rebase to move work).

If a git command seems necessary, stop and re-read: jj has an equivalent
for every common git operation.

## Bookmarks mark, they do not develop

Work is a linear stack of commits on the current tip. `@` is the
working-copy commit; describe it, then `jj new` to advance. There are no
development branches. The protected main bookmark trails the tip and
advances to the latest landed commit only at land time, by the
maintainer. Never base work on main: forking off main when main lags the
tip forks history. A bookmark per major deliverable is named in plain
words for what shipped, placed on the last landed commit with `@`
already advanced. A bookmark never doubles as a dev branch.

## Workflow

1. **Inspect.** `jj st` and `jj diff -r @`. Confirm the change is what
   you intend.
2. **Verify single-purpose.** A change owns one logical effect. If the
   diff shows two unrelated changes, split them: `jj new` for the second
   piece.
3. **Describe.** `jj describe -m "Category: Imperative subject"`. If the
   change already has a description but the work evolved, `jj describe
   -m` replaces it; jj has no amend ceremony.
4. **Advance.** `jj new` for a fresh empty change on top. The described
   change is now history, immutable until rebased or squashed.
5. **Squash if you split too eagerly.** `jj squash` folds the current
   change into its parent.

## Message format

One line. The line is the whole message. No body, no trailers, no
exceptions. The diff is the documentation; the subject is the index
entry. Form: `Category: Imperative subject`.

Hard rules:

- **One line. No body. No trailers.** No `Co-Authored-By`,
  `Generated-with`, `Signed-off-by`, or any AI or tool attribution. The
  commit is by the author of the change.
- **Category first**, from the list below.
- **Capitalize the first word after the colon.**
- **Imperative mood.** Reject, not Rejects.
- **No trailing period.**
- **Within 70 characters**, including category and colon.
- **Effect, not diff.** What the change does for the reader, not what
  the code now contains.
- **No version numbers.** Release metadata, not commit content.
- **No process IDs, and no ID as a pseudo-category.** No phase, task,
  slice, or run id; no plan or decisions reference; no retro label. A
  numeric prefix standing in for the category is banned; the category is
  a real one from the list. Durable refs (an ADR, a design doc by path)
  are fine.
- **No em dashes.** Restructure if tempted.

### The categories

Cross-cutting: `Scaffold`, `Build`, `Tests`, `Docs`, `Fix`, `Refactor`,
`Plans`, `Serve`, `CI`. Module or area names double as categories where
they aid scanning (the names in the project's module map: a core, shell,
renderer, or platform area). `Skills` covers the agent system itself.
Pick the one that names the area the change touches so the log scans. If
none fits, leave it uncategorized rather than invent a hollow one.

### Good vs bad

| Bad | Good | Why |
|---|---|---|
| `Native: Add nil check in load path` | `Native: Reject zero-length data block on load` | effect (reject) not mechanism (nil check) |
| `Core: Various fixes to query` | `Core: Return empty for a no-match filter` | various is hollow; name the change |
| `Refactor: Clean up code` | `Refactor: Extract shared helpers from query module` | name what was extracted and from where |
| `Fix: bug in playback` | `Playback: Stop voice on engine free, not after` | name the area and the effect |
| `UI: Improvements` | `UI: Cache hit-test results when the view-spec is unchanged` | improvements says nothing; the effect is the cache |
| `Persist: Update schema` | `Persist: Add source-format attribute` | name what was added |
| `Docs: Update docs` | `Docs: Document the native boundary handle lifetimes` | docs twice is noise; name the topic |
| `Tests: Add more tests` | `Tests: Cover query edge cases (nil, empty, single)` | name the area and the edges |
| `Native: Make it faster` | `Native: Vectorize the inner loop with a wide SIMD type` | mechanism is the measurement |
| `Skills: Tweak` | `Skills: Add write-commit skill for the jj workflow` | name what landed |
| `phase-3: Detect peak from envelope` | `Native: Detect the peak from the onset envelope` | a process ID is never the category |

### Word choice

- **Verbs that work:** Add, Reject, Extract, Return, Stop, Cache,
  Vectorize, Document, Cover, Fix, Remove, Replace, Rename, Hoist, Gate,
  Bound, Wrap, Pin, Reset, Track, Group, Sort.
- **Verbs to avoid:** Improves, Fixes (without naming what), Cleans up,
  Tweaks, Updates (without naming what), Handles, Supports (without
  naming what), Various, Several, Some.
- **Nouns to avoid:** things, stuff, code, miscellaneous, misc, general,
  various.
- **Adjectives to avoid:** better, faster (without a number), cleaner,
  nicer, more robust, more reliable. Name the measurement or mechanism,
  not the adjective.
- **Hedges to avoid:** probably, seemingly, apparently, may, might,
  could. The commit does the thing or it does not.

### The subject as a sentence

Read alone, the subject is a sentence: in the Native module, reject a
zero-length data block on load. If it will not parse as a sentence, it
is malformed. The category is the implied prelude: in this area, do
this.

### Pre-flight before describing

Before `jj describe -m "..."`:

1. Reads as a sentence aloud?
2. Within 70 characters, category and colon included?
3. Effect, not diff?
4. Imperative mood?
5. Specific?
6. Free of AI tells (no em dash, no utilize, no leverage, no robust, no
   seamless)?
7. Free of attribution (no Co-Authored-By, no Generated-with, no tool
   trailer)?
8. Free of process IDs (no phase, task, slice, or run id, no plan
   reference, no numeric prefix for the category)?

Any no, rewrite. For deeper craft, invoke write-prose first.

## When to commit

- One logical change per commit. Two effects, two commits.
- Behavior and acceptance scenarios commit red on their own. They are
  the spec; they sit red until the units under them land. This is the
  one place a described change may fail.
- Fine-grained unit tests ship in the same change as the code that turns
  them green. A unit test and its implementation are one change, so
  jj-driven bisection always lands on a working state.
- A change is ready to describe when the diff is reviewable on its own:
  it builds, passes its owning tests (a red spec excepted), and leaves
  no half-finished tree.

Do not commit:

- Generated artifacts the project does not track.
- Secrets, credentials, license keys.
- Stale commented-out code. Delete it; jj holds history.
- Drive-by renames or style churn in untouched code. That is its own
  change.

## Colocation

jj imports the underlying git history on every operation. A change
described via `jj describe` becomes a git commit when pushed; a git
commit imported into jj becomes a jj change. Prefer jj for new work; push
with `jj git push`.

## Boundaries

Owns: the commit-specific form (the jj workflow, the single-line
category-first subject, the category list, the pre-flight check, the
red-green commit choreography). Cites: write-prose for the prose craft;
`skills/shared/references/prose-style.md` for the project surface
conventions. Siblings: write-prose owns the prose craft; write-changelog
owns the user-facing counterpart; write-tests owns the red-green
choreography that feeds this skill. If the message is wrong, describe
again; do not amend by guessing.

## References

- write-prose (sibling): the prose craft the subject rides on.
- `skills/shared/references/prose-style.md`: the project's surface
  conventions; this skill is the canonical home for the commit form.
- write-tests: the TDD commit choreography this skill enforces.
