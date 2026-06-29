---
name: incorporate-feedback
description: Promote captured guidance from the inbox into its durable home, empty the inbox, one commit.
user-invocable: false
---

# incorporate-feedback

Drain the guidance inbox into the durable standards: each entry to its
strongest home, conflicts resolved, the inbox emptied in one commit.

## Procedure

Input: `~/.agentic-sdk/<project>/guidance/inbox.edn`. Per entry, in inbox
order:

1. **Pick the home by strength.** Strongest first:
   - **Lint rule.** When the rule is mechanically checkable, a rule
     that fails on violation beats prose. Add it through the house
     rules the `compile-rules` spine task projects, or the project's
     detected      linter, plus a test that the lint catches it.
   - **Reference file.** The `references/` docs and the language style
     files; rules reviewers and writers must apply.
   - **`check-*` / `write-*` skill body.** When it changes what a
     dimension looks for or how a recipe proceeds.
   - **ADR** (`docs/adr/`, via `record-decision`): when the entry is a
     why (a choice between alternatives) rather than a how. Often
     paired: the ADR holds the reasoning, a reference holds the rule.
2. **Resolve conflicts.** An entry with `:conflicts-with`, or one you
   discover contradicting current text: present both rules to the
   maintainer and apply the decision; never keep both. The resolution
   is a decision; record it via `record-decision`.
3. **Write it in place,** in the file's voice, a rule not a changelog
   of the conversation. Delete the entry from `inbox.edn` in the same
   change.
4. **Cross-check.** If the new rule invalidates an example elsewhere
   (a now-banned idiom shown as good, a doc that contradicts the
   rule), fix those sites too.
5. **Leave the profile alone.** Interaction preferences live in
   `profile.edn` and are read by every session; re-tune them only when
   the maintainer asks, not as part of this pass.

Commit as one `Skills: Incorporate captured guidance (<n> entries)`.

## Boundaries

Owns the inbox-to-standards promotion only. Capturing is
`capture-guidance`; recording an architectural why is
`record-decision`. Edits the standards layer (references, skills, lint
rules, ADRs); does not change run state or source code.

## Return

A rule-to-home table, one row per entry promoted.
