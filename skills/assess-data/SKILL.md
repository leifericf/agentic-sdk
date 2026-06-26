---
name: assess-data
description: Plan schema changes, migrations, and backfills
user-invocable: false
---

# assess-data

Plan data migrations, backfills, and rollback strategy. Declarative and
stack-agnostic; prefer reversible changes; flag fragile rollbacks.

## Procedure

1. Read the dispatched inputs: the Gherkin spec, feature scope, and
   technical context (data model, store type).
2. Determine applicability. Required when the feature adds, modifies, or
   removes columns or tables, changes data formats, requires backfilling, or
   introduces new entities or relationships. N/A when purely visual,
   behavior-only logic with no data impact, or read-only over existing data.
3. If Required, name the migration type (schema-only, backfill,
   expand/contract) and why. Define the up migration (DDL or DML, breaking
   changes). Define the down migration when safe; if fragile, say why.
4. For expand/contract, give the three steps: expand (add without removing),
   deploy (write to both), contract (remove old after verification). For
   backfill, state online or offline, idempotency, restartability, and
   partial-rollback behavior.
5. State rollback considerations: what happens if the migration fails or the
   deploy is reverted, and any consistency risks. Prefer separate deploys for
   schema migrations and data backfills; never combine a large rewrite with
   DDL in one migration.

## Boundaries

Owns the data and migration strategy. assess-rollout owns deployment;
plan-feature owns folding the result into the plan. Reached by plan-feature's
fan-out.

## Return

One EDN map: `{:applicability :required|:n/a :n/a-reason <str>
:migration-type ... :up ... :down ... :expand-contract <str|nil> :backfill
<str|nil> :rollback ... :safety ...}`. For N/A, only `:applicability` and
`:n/a-reason`.
