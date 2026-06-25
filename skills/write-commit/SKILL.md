---
name: write-commit
description: Recipe for any commit in this project. Mandates jj (Jujutsu) over git, the single-line category-first message form, and the jj workflow. Invoke before any commit operation, by any agent or by the maintainer.
user-invocable: false
---

# write-commit

Invoke this skill before any commit. The project uses jj (Jujutsu) as
the VCS, colocated with git for remote compatibility. Never use raw git
commands for committing; jj owns the change model.

**Read first: write-prose.** This skill owns the commit-specific form
(the jj workflow, the single-line category-first subject, the category
list, the pre-flight check). write-prose owns the prose craft the
subject rides on (active voice, concrete over general, no AI tells, no
marketing adjectives). Invoke write-prose first when drafting any
subject that takes more than a moment's thought; then invoke this skill
to apply the commit-specific form.

The two skills compose:
write-prose (craft) plus write-commit (form) equals a project commit.

## jj, not git

The repo has both `.git/` and `.jj/`. jj is the source of truth for the
working change. git is the wire format for the remote.

Commands you must use:

- `jj st` to inspect working-copy status (replaces `git status`).
- `jj log --limit N` to read history (replaces `git log`).
- `jj diff -r @` to see the current change (replaces `git diff`).
- `jj describe -m "..."` to set the message on the current change
  (replaces `git commit -m`).
- `jj new` to move to a fresh empty change on top of the current one.
- `jj squash` to fold the current change into its parent.
- `jj unsquash` to split a folded change back out.
- `jj rebase -r <rev> -d <dest>` to rebase a change onto another.
- `jj git push` to push to the remote (replaces `git push`).

Commands you must not use:

- `git commit`, which bypasses jj's change model. Use `jj describe` then
  `jj new`.
- `git add` and `git rm`, which have no jj equivalent: jj auto-tracks
  working-copy mutations, no staging step exists.
- `git stash`, which jj has no analogue for. Use `jj new` to start a
  fresh change; the current one is preserved as-is.
- `git rebase`, which has a jj equivalent that understands colocation.
- `git am` and `git apply`, replaced by jj squash or rebase to move work.

If a git command appears necessary, stop and re-read this skill. jj has
an equivalent for every common git operation.

## Bookmarks mark, they do not develop

Work is a linear stack of commits on the current tip. `@` is the
working-copy commit; describe it, then `jj new` to advance. There are no
development branches, and you never base work on the protected main
bookmark: forking off main when main lags the tip forks history.
Bookmarks are deliberate markers. The protected main bookmark trails the
tip and advances to the latest landed commit only at land time, by the
maintainer. A bookmark per major deliverable is named in plain words for
what shipped, placed on the last landed commit with `@` already advanced
to a fresh empty change. A bookmark never doubles as a dev branch.

## Workflow

1. **Inspect.** `jj st` shows working-copy changes. `jj diff -r @` shows
   the actual diff. Confirm the change is what you intend.
2. **Verify single-purpose.** A change owns one logical effect. If
   `jj diff` shows two unrelated changes (a fix plus a refactor, a
   feature plus a doc update), split them: `jj new` to start a fresh
   change for the second piece, leaving the first as its own change.
3. **Describe.** `jj describe -m "Category: Imperative subject"`. The
   message format is below. If the current change already has a
   description but the work has evolved, `jj describe -m` again replaces
   it; jj has no amend ceremony.
4. **Advance.** `jj new` creates a fresh empty change on top of the
   current one. Do this after describing so the next change starts clean.
   The described change is now in the history, immutable until rebased or
   squashed.
5. **Squash if you split too eagerly.** If you `jj new` early and then
   realize the work belongs with the parent, `jj squash` folds the
   current change into its parent.

## Message format

This is the canonical home for the project's commit message style. Other
skills (write-prose, `skills/shared/references/prose-style.md`) defer
here.

### The form

A commit subject is exactly **one line**. The line is the whole commit
message. No body, no trailers, no exceptions, no matter how complex the
change. The diff is the documentation; the subject is the index entry.

The line takes the form: `Category: Imperative subject`.

Hard rules:

- **One line. No body. No trailers.** Never add a body paragraph, even
  when the change is complex. Never add a Co-Authored-By trailer, a
  Generated-with line, a Signed-off-by for a bot, or any other AI or
  tool attribution. The commit is by the author of the change.
- **Category first.** Pick from the canonical list below.
- **Capitalize the first word after the colon.**
- **Imperative mood.** Reject, not Rejects. Add, not Adds.
- **No trailing period.**
- **Within 70 characters**, count includes the category and colon.
- **Effect, not diff.** Describe what the change does for the reader,
  not what the code now contains.
- **No version numbers.** Versions are release metadata, not commit
  content.
- **No internal process IDs, and no ID as a pseudo-category.** No phase,
  task, slice, or run identifier, no plan or decisions file reference,
  no retro label. A plan, phase, or task ID is never the leading
  category: any numeric prefix standing in for the category is banned.
  The category is a real one from the list below; strip any process ID
  off a planned-commit string before it reaches `jj describe`. The
  subject names the change's effect, not the run that produced it.
  Durable refs (an ADR, a design doc by path) are fine. See write-prose.
- **No em dashes.** Restructure if tempted.

### The categories

The cross-cutting categories: `Scaffold`, `Build`, `Tests`, `Docs`,
`Fix`, `Refactor`, `Plans`, `Serve`, `CI`. Module or area names double
as categories where they aid scanning (the names in the project's module
map, such as a core, shell, renderer, or platform area). `Skills` covers
the agent system itself. Use the one that names the area the change
touches so the log scans. If no category fits, leave it uncategorized
rather than invent a hollow one.

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
| `phase-3: Detect peak from envelope` | `Native: Detect the peak from the onset envelope` | a process ID is never the category; lead with a real one |

### Word choice

- **Verbs that work:** Add, Reject, Extract, Return, Stop, Cache,
  Vectorize, Document, Cover, Fix, Remove, Replace, Rename, Hoist,
  Gate, Bound, Wrap, Pin, Reset, Track, Group, Sort.
- **Verbs to avoid:** Improves, Fixes (without naming what), Cleans up,
  Tweaks, Updates (without naming what), Handles, Supports (without
  naming what), Various, Several, Some.
- **Nouns to avoid:** things, stuff, code, miscellaneous, misc, general,
  various.
- **Adjectives to avoid:** better, faster (without a number), cleaner,
  nicer, more robust, more reliable. If a property matters, name the
  measurement or the mechanism, not the adjective.
- **Hedge words to avoid:** probably, seemingly, apparently, may, might,
  could. The commit either does the thing or it does not.

### The subject as a sentence

Read alone, the subject should be a sentence: in the Native module,
reject a zero-length data block on load. If you cannot read it as a
sentence, the subject is malformed. The category is the implied prelude:
in this area, do this.

### Pre-flight before describing

Before `jj describe -m "..."`, run the subject through this check:

1. Read it aloud as a sentence. Does it parse?
2. Within 70 characters including the category and colon?
3. Effect, not diff?
4. Imperative mood?
5. Specific?
6. Free of AI tells (no em dash, no utilize, no leverage, no robust, no
   seamless)?
7. Free of attribution (no Co-Authored-By, no Generated-with, no tool
   trailer)?
8. Free of internal process IDs (no phase, task, slice, or run id, no
   plan reference, no retro label, and no numeric prefix standing in for
   the category)?

If any answer is no, rewrite before describing.

For the deeper prose craft (active voice, concrete over general, no AI
tells, no marketing adjectives), invoke write-prose first when drafting
the subject. This skill owns the commit-specific form; write-prose owns
the prose craft the form rides on.

## When to commit

- One logical change per commit. A change that does two things is two
  commits.
- Behavior and acceptance scenarios commit red on their own. They are
  the spec; they sit red until the units under them land. This is the
  one place a described change is expected to fail.
- Fine-grained unit tests ship in the same change as the code that turns
  them green. A unit test and its implementation are one change, never
  two, so jj-driven bisection always lands on a working state.
- A change is ready to describe when the diff is reviewable on its own:
  it builds, it passes its owning tests (a red spec excepted), it does
  not leave the tree in a half-finished state.

Do not commit:

- Generated artifacts the project does not track (see the ignore file).
- Secrets, credentials, license keys.
- Stale commented-out code. Delete it; jj holds history.
- Drive-by renames or style churn in untouched code. That belongs in its
  own change, not folded into unrelated work.

## Colocation notes

jj imports the underlying git history on every operation. A change
described via `jj describe` becomes a git commit when pushed. The
reverse is also true: a git commit imported into jj becomes a jj change.
Prefer jj for new work; use `jj git push` (not `git push`) for remote
operations.

## Boundaries

Owns: the commit-specific form (the jj workflow, the single-line
category-first subject, the category list, the pre-flight check, the
red-green commit choreography). Cites: write-prose for the prose craft,
`skills/shared/references/prose-style.md` for the project surface
conventions. Siblings: write-prose owns the prose craft; write-changelog
owns the user-facing counterpart; write-tests owns the red-green
choreography that feeds this skill. Does not amend a described change by
guessing; if the message is wrong, describe again.

## References

- write-prose (sibling): the prose craft the subject rides on.
- `skills/shared/references/prose-style.md`: the project's surface
  conventions, including the commit-message form this skill is the
  canonical home for.
- write-tests: the TDD commit choreography this skill enforces.
