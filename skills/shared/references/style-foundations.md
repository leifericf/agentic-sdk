# Style foundations: the writing craft

Two books anchor the prose standard for this project. Everything below
is distilled from them. Read the books if you can; this is the working
summary the project writes against.

- William Strunk Jr. and E.B. White, *The Elements of Style* (4th ed.,
  Longman, 2000)
- William Zinsser, *On Writing Well* (30th anniversary ed., Harper
  Paperbacks, 2006)

The two converge on a single discipline: **cut, then cut again, until
only the load-bearing words remain.** Style is what you remove, not
what you add.

## Strunk and White: the rules that bear on technical prose

The most load-bearing rules, from Chapter V "An Approach to Style" and
Chapters I to IV:

- **Omit needless words.** The cardinal rule. Every word that fails to
  add to the meaning takes away from it. A sentence should contain no
  unnecessary words, a paragraph no unnecessary sentences, for the
  same reason a machine should have no unnecessary parts.
- **Use the active voice.** "The macro normalizes the signature," not
  "the signature is normalized by the macro." The active is direct,
  compact, and names the agent.
- **Put statements in positive form.** "Unsupported files are rejected
  with a clear error," not "unsupported files are not accepted." The
  negative is wordier and weaker; the positive names the behavior.
- **Use definite, specific, concrete language.** Name the file, the
  type, the number, the path. "A 1024-sample window" carries more than
  "the window." General terms are a finding.
- **Omit a succession of loose sentences.** A chain of coordinate
  clauses is monotonous; subordinate or contract.
- **Express coordinate ideas in similar form.** Parallelism.
  Corollary: if you change the grammar, the reader expects the meaning
  to change.
- **Keep related words together.** The modifier next to the word it
  modifies; the subject next to the verb. Misplacement forces the
  reader to re-read.
- **In summaries, keep to one tense.** This project's tense is present
  for current behavior ("the analysis module extracts the feature"),
  past for history ("the spike showed").
- **Place the emphatic words at the end.** The end of a sentence is
  the stress position. Lead with the topic, end with the point.
- **Avoid overwriting.** Rich, ornate prose is harder to read, not
  easier. Reject the picturesque.
- **Avoid overstating.** Hyperbole loses the reader's trust. If the
  matter is truly important, the plain statement carries it.

## Zinsser: the principles that bear on technical prose

From *On Writing Well*:

- **Simplicity.** "Clutter is the disease of American writing." Strip
  every word with no function, every long word that could be short,
  every adverb that repeats the verb's meaning, every passive
  construction that hides who is acting.
- **Clutter.** Most first drafts can be cut by 50%. The second draft
  is the first draft minus 10 to 15% more. "The secret of good
  writing is to strip every sentence to its cleanest components."
- **Style is subtraction.** "Few writers realize that the final
  product is a series of self-edits." Style emerges from what you
  refuse to keep, not from what you add. Ornamentation does not
  produce style; it produces clutter.
- **Words.** Pick the word that does the work; reject the one that
  decorates. "The race is to the writer who gets there first with the
  most."
- **The audience.** Write for yourself first; edit for the reader. If
  a sentence loses you when you re-read it, it will lose them.
- **Usage.** Reject fads and jargon. They date the prose and exclude
  readers.
- **Unity.** One tone, one tense, one point of view per piece. The
  project holds this at the section scope: one topic per file.

## The unified craft (eight load-bearing rules)

Strunk and Zinsser converge on the rules the project writes against:

1. **Cut.** The first draft is too long. The second draft is shorter.
   So is the third.
2. **Active voice.** Name the agent of the action.
3. **Concrete over general.** Name the file, the type, the number.
4. **Positive form.** Say what IS, not what IS NOT.
5. **Related words together.** Modifier next to modified, subject
   next to verb.
6. **End on the point.** The end of the sentence is the stress
   position.
7. **One tense per scope.** Present for current behavior, past for
   history.
8. **No decoration.** No adverb that repeats the verb; no adjective
   that softens the claim.

## The AI tells (the project bans these)

These patterns mark prose as machine-generated. Every one is a finding
during review, and every one should be caught by the writer before the
prose lands.

- **Em dash.** Never use the em dash (`—`). Restructure the sentence:
  a comma, a colon, a period, or rewrite. Reaching for the em dash is
  a signal that the sentence is doing too much.
- **Arrow in prose.** Never `->` in prose. Write "to", "then", or
  restructure.
- **"hand-written" / "hand-rolled".** Never. Use "ordinary", or just
  omit the adjective.
- **Hollow hedging.** "It's worth noting that", "It should be noted
  that", "It is important to recognize that", "Needless to say".
  Delete the hedge; state the thing.
- **Filler openers.** "In today's fast-paced world", "As the digital
  landscape evolves", "In the modern era of". Cut.
- **List-of-three with a climax.** "Fast, reliable, and scalable." The
  third item is usually filler. Two is enough if two is the truth.
- **False affinity adjectives.** "Powerful", "robust", "seamless",
  "comprehensive", "modern", "blazing", "elegant", "advanced",
  "cutting-edge". If a property matters, state the measurement or the
  mechanism, not the marketing word.
- **Sympathetic throat-clearing.** "I'd be happy to help",
  "Certainly!", "Of course!", "Great question!". Cut.
- **Machine apology.** "As an AI", "As a language model", "I cannot",
  "I'd recommend". Cut; state the claim.
- **Bullet-by-rewrite.** Each bullet restates the previous with a
  synonym. Each bullet should carry a different idea, or it should not
  be a bullet.
- **Rhetorical-question transition.** "So, what does this mean for
  users?" Just state what it means.
- **Topic sentence that previews AND summarizes.** Lead with the
  point; do not preview it. Do not summarize it at the end either.
- **"Not only ... but also"** when one side would do.
- **Capitalized abstractions.** "Quality", "Performance",
  "Reliability", "Security": lowercase unless it names a proper noun
  or a specific measurement.
- **Em-dash pendant clauses.** A sentence that interrupts itself with
  a dashed insertion is doing too much. Use parentheses, two
  sentences, or restructure. The ban is the same as the standalone em
  dash; this entry names the specific pattern.
- **"In order to".** Just "to". Always.
- **"Utilize" / "leverage" / "facilitate".** Use "use", "use", "ease",
  or restructure.
- **"There is" / "There are" openers.** Restructure to put the subject
  first. "There are three modules that..." becomes "Three
  modules...".
- **Trendy intensifiers.** "Really", "very", "super", "incredibly",
  "highly". Delete; let the noun carry the weight.

## Pre-flight before submitting any prose

Run the prose through this check before considering it done:

1. Read it aloud once. The ear catches clutter the eye misses.
2. Cut by 10%. If you cannot find 10%, read again.
3. Search for `—`. If any appears outside the ban catalog itself,
   restructure.
4. Search for adverbs ending in `-ly`. Most can go.
5. Search for "that". Half of them are filler.
6. Search for the AI tells above. Find one, fix one.
7. End each sentence on its point, not its preamble.
8. Check parallelism in lists and headings. Coordinate ideas in
   coordinate form.

## When the books and the project disagree

The project's `prose-style.md` is the final word on project-specific
surfaces (commit format, ADR format, code-comment conventions, the
no-process-ID rule). The books are the foundation; project conventions
narrow them. When `prose-style.md` is silent, the books decide.

---

*Sources: Strunk, William Jr., and E.B. White. The Elements of Style.
4th ed. Longman, 2000. Zinsser, William. On Writing Well. 30th
anniversary ed. Harper Paperbacks, 2006.*
