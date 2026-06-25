---
name: design-ui
description: Recipe for designing a UI surface before it is implemented, the four-layer procedure (intent, information architecture, interaction, presentation) chosen from tokens, and the full set of states. Invoked before write-ui when a new or changed UI surface needs a design.
user-invocable: false
---

# design-ui

Design a UI surface before it is built. This recipe produces a design
spec: the surface's intent, its information architecture, its
interactions, and its presentation chosen from the tokens. It precedes
write-ui, which implements the spec. Design first, then implement; do
not decide the design inside the implementation.

The design spec is the deliverable. It can be a short doc, a section in
a feature's notes, or an annotated view-spec sketch. It is data and
prose, never running code.

Read first:

- the project's design-language reference: the principles, the layered
  model, the token system, the component template, state design, and the
  design-review checklist.
- the project's view-spec reference: the widget vocabulary the design
  composes.
- the project's token catalog: the tokens the presentation step chooses
  from.
- write-ui (sibling): the implementation recipe this design precedes.
- check-design: the review dimension the design is held to.

## The procedure

Work the four layers in order. Never start with visuals.

1. **Intent.** State the one thing the user is trying to accomplish on
   this surface, in one sentence. If you cannot state it, the surface is
   not designed yet.
2. **Information architecture.** List the information that serves the
   intent. Mark what is primary, what is secondary, what can be hidden
   behind progressive disclosure, and what belongs together. Place the
   surface in the nesting the view-spec reference defines (section,
   view, panel).
3. **Interaction.** For each thing the user does, name the intent, the
   action, and the binding: the action keyword and the click or key that
   triggers it. Cover selection, navigation, and editing. Keep these
   familiar; the novelty budget is spent on presentation, not on how to
   select.
4. **Presentation.** Only now choose the visuals, all from tokens:
   - The widgets from the vocabulary; reach for an existing type before
     proposing a new one.
   - The depth layer for each widget.
   - The motion for appearance, change, and exit; the camera move where
     the surface is spatial.
   - The spacing, type, and color tokens; never a raw value.
   - The density mode or modes the surface supports.

## Design every state

The design is not done until every state is designed. For this surface,
specify:

- **Empty**: no data yet, or a filter with no match. What guides the
  next step?
- **Loading**: a load or transform in progress. What shows progress, and
  what stays usable?
- **Success**: the populated, interactive state.
- **Error**: a load failed, a resource is missing. What names the cause?
- **Permission denied**: a resource the OS will not read. What names the
  fix?

Do not invent a state the product cannot reach.

## Check the design

Before handing off, run the design-review checklist: the user goal, the
simplest version, first-time understanding, expert speed, motion and
depth that communicate, the states, accessibility (focus visible,
contrast, reduced-motion), the frame budget, and consistency with
existing patterns and tokens.

## Always

- **Tokens, not raw values.** Every spacing, size, color, duration, and
  depth in the spec names a token from the catalog. A raw value in a
  design spec becomes a raw value in the view-spec.
- **Reuse the vocabulary.** A new widget type is a design commitment;
  propose one only when no existing type parameterized would serve, and
  when it is a real choice between alternatives, record it with
  record-decision before it spreads.
- **Familiar where it counts, novel where it pays.** Navigation,
  selection, editing, and confirmation stay conventional; spend the
  novelty budget on motion, depth, and presentation.
- **Hand off to write-ui.** The design spec is the contract the
  implementation builds against; the implementer does not re-decide the
  design.

## Boundaries

Owns: the design spec (intent, information architecture, interaction,
presentation, states) for one UI surface. Cites: the project's
design-language reference, view-spec reference, and token catalog.
Siblings: write-ui owns the implementation of the spec; check-design
owns the review dimension; check-clarity owns the prose of the spec
itself; write-prose owns the prose standard. Produces data and prose,
never running code; the design decision that belongs in the architecture
goes to record-decision, not into this spec.

## Returns

One line: the surface designed, the spec's location, and whether every
state is covered.
