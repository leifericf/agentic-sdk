---
name: write-changelog
description: Recipe for changelog entries, when a change deserves one, the entry voice, the category-first form, and the release-cutting ritual. Invoked by writers and editors when authoring entry lines, and at release time.
user-invocable: false
---

# write-changelog

The changelog is written for users of the project; the commit log is
written for maintainers. Same change, two audiences. Voice rules:
`skills/shared/references/prose-style.md`. Mechanics: during a fix loop
the editor and writer agents do not edit the changelog file, they return
their `CHANGELOG:` lines (see apply-findings), the orchestrator holds
them in context, and places them into the changelog at land time. The
changelog is the committed, durable artifact; the lines reach it as
return values, never as files read back from disk.

## Does it deserve an entry?

Yes: behavior a user can observe changed. A new surface (a menu, a view,
a scripting entry point), a fix to wrong output, a crash, a glitch,
performance a user would notice (frame rate, throughput), anything
security-relevant (always), removed or changed surface.

No: refactors, comment or doc fixes, test additions, CI or tooling work,
skill-system changes. Those live in commit messages. Returning no
`CHANGELOG:` line for a fix is normal and correct.

## The entry

- One returned `CHANGELOG:` line, `Category: <new observable behavior>`,
  with the category matching the commit's category so the section groups.
  The user-facing categories come from the project's module map (the
  surface areas a user sees). Maintainer-facing categories (Tests,
  Build, Skills, CI, Refactor) are commit-only, not changelog.
- Lead with the effect: what now happens. Old behavior or cause follows
  only if it earns its space.
- One to five lines once wrapped; mechanism detail beyond that belongs
  in the commit or an ADR.
- The changelog talks only about the changes themselves: never reference
  plan or cycle names, round or phase numbers, or finding or task ids.
  That provenance lives in commits and ADRs.

## Cutting a release

At release time (maintainer-driven):

1. Read the changelog's Unreleased section. If the changelog does not
   exist yet (first release), create it with a top heading followed by
   `## Unreleased` and the accumulated `CHANGELOG:` lines as the initial
   Unreleased bullets.
2. Pick the version bump per semver: a new surface or a behavior change
   is a minor bump; a fix-only release is a patch; a removed surface is a
   major. Below 1.0, treat the version as 0.X.Y.
3. Title the section after its dominant theme, the way the design docs
   talk about features, not after the version number.
4. Move the Unreleased section under the new version heading; start a
   fresh Unreleased above it. Commit on main:
   `Docs: Cut vX.Y.Z, <short title>`.

If the Unreleased section is empty, refuse the cut: a release with no
user-visible changes is a smell.

## Boundaries

Owns: whether a change deserves a changelog entry, the entry voice and
form, and the release-cutting ritual. Cites:
`skills/shared/references/prose-style.md` for the voice. Siblings:
write-prose owns the prose craft the entries ride on; write-commit owns
the maintainer-facing counterpart; ship invokes this skill at release
time; apply-findings returns the `CHANGELOG:` lines this skill places.
Does not edit the changelog file mid-fix-loop; it returns lines the
orchestrator holds and lands.
