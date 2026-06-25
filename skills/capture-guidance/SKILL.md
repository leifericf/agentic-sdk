---
name: capture-guidance
description: Capture a correction or interaction preference mid-session at near-zero cost, route it to the right home, and return to the work.
user-invocable: false
---

# capture-guidance

One capture primitive, two routes: a correction goes to the guidance
inbox for later promotion; an interaction preference goes to the
profile every session reads.

## Procedure

1. **Classify the input.**
   - A correction or ruling about the work or the process ("don't do
     X", "always Y here", "that pattern is banned"): route to the
     inbox.
   - An interaction preference (verbosity, clarifying questions per
     turn, ask-first versus decide-and-continue, probing depth, output
     style, hard red lines): route to the profile.
2. **Correction route.** Append one entry to
   `.claude/guidance/inbox.edn` (a vector; `[]` if missing; commit it
   so all maintainers share one inbox):

   ```edn
   {:date "YYYY-MM-DD"
    :rule "one imperative sentence, the way a reviewer would state it"
    :context "what happened that triggered the correction"
    :applies-to :clojure | :zig | :c | :elixir | :native-edge | :persistence | :ui | :tests | :tooling | :process
    :suggested-home "check-style/references/<lang>-style.md"}
   ```

   `:applies-to` reflects the project's surfaces: the four languages;
   `:native-edge` for any C ABI, NIF, or foreign-function boundary;
   `:persistence` for the shell's store and transactions; `:ui` for
   the UI surface; `:tests` for the suite; `:tooling` for build, deps,
   or release tooling; `:process` for anything cross-cutting.
   `:suggested-home` is your best guess: a reference file, a
   `check-*` or `write-*` skill body, a future ADR, or a lint rule
   when the rule is mechanically checkable. If the correction
   contradicts an existing rule, set `:conflicts-with "<file>"`;
   `incorporate-feedback` resolves it with the maintainer.

3. **Preference route.** Upsert the preference into
   `.claude/guidance/profile.edn` (a map; `{}` if missing; committed).
   One key per preference, plain values:

   ```edn
   {:verbosity :concise                       ; :concise | :balanced | :detailed
    :questions-per-turn 2
    :default-behavior :decide-and-continue    ; :ask-first | :decide-and-continue | :decide-silently
    :probing-depth :standard                  ; :light | :standard | :deep
    :output-style :structured                 ; :structured | :prose | :mix
    :red-lines ["never advance main autonomously"]}
   ```

   Set only the keys the maintainer stated; never infer. Never store a
   secret; if one appears, tell the maintainer to remove it and do not
   record it.

4. **Return to the work.** Confirm in one line and resume the
   interrupted task. Do not refactor skills mid-task. The inbox is
   append-only between incorporation passes; never edit or delete
   entries here.

## Boundaries

Owns the single capture step only. Promotion of inbox entries into the
shared standards is `incorporate-feedback`; a real architecture choice
that settles a decision is `record-decision`. Does not edit skills,
references, ADRs, or source from here.

## Return

`captured: <rule>` (for a preference, the value phrased as an imperative).
