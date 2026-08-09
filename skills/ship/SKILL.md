---
name: ship
description: Cut a release end to end, from lane gate to tag and trunk advance
disable-model-invocation: true
---

# ship

Role: the release cut. Takes the current tip to a tagged release:
verifies the pre-land lanes, writes the changelog, assesses release
risk, tags, and advances trunk. Folds the old merge-to-trunk and
prepare-release steps into one cycle.

## Prerequisites

`~/.agentic-sdk/<project>/project.edn` exists with `:lanes :pre-land` and `:vcs`. The tip
is at a state the maintainer wants to release. A target version (or a
recommendation the maintainer approves at the gate).

## Procedure

1. **Verify the pre-land lanes.** Dispatch one verifier running
   verify-lanes against the `:pre-land` lane set from the descriptor.
   This is the release gate: it must pass clean. FAIL feeds back to
   fix-bug or implement-change; the release does not proceed on a
   failing gate.
2. **Write the changelog (if the project keeps one).** Dispatch
   write-changelog. It checks the project stage first: alpha and beta
   projects keep no changelog, and the dispatch returns immediately with
   no entry. For stable projects it reads the commit log since the last
   release tag, groups changes by category, drops internal-only churn,
   and writes the release section. Determine the version bump from the
   landed changes: breaking changes force major, new capabilities force
   minor, fixes force patch. Recommend; the maintainer decides at the
   gate.
3. **Assess release risk.** Dispatch assess-risk against the landed
   changes. It maps the blast radius, dependencies and their failure
   behavior, the rollout strategy, monitoring and rollback plan, the
   risk register, and the go/no-go criteria. It writes
   `ops/<date>_risk_<slug>.md`. Hold its one-line return.
4. **Advance trunk, then tag.** The tip is a linear stack ready to
   land. Advance the protected main bookmark over the stack: with jj,
   `jj bookmark set main -r <tip>`, then push. This is the
   merge-to-trunk step folded in. Then tag at main: with jj
   (git-compatible), `git tag -a v<version> -m "<version>"` and push
   the tag.
5. **Present the gate.** Show the maintainer the changelog (if any),
   the risk assessment path, the recommended version, and the tag. The
   tag is the approval gate. On approval, apply the tag and advance
   trunk. An autonomous run never tags or advances main without the
   maintainer's yes.

## Boundaries

Reads only the one-line returns of verify-lanes, write-changelog, and
assess-risk. It does not fix code: a failing gate hands back to fix-bug
or implement-change. It does not advance main or tag without the
maintainer's approval at the gate. The tag and the trunk advance are the
one approval gate. Atoms dispatched: verifier (verify-lanes against
`:pre-land`), write-changelog, assess-risk, write-commit for the
changelog commit when the project is stable and keeps the changelog
committed.

## Return

One line: the version, the tag, the trunk position, the changelog path
(or "no changelog" for alpha/beta projects), and the risk assessment
path, with the pre-land lane verdict.
