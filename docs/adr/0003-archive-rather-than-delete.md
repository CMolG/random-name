# ADR-0003: Archive as a soft state change, and `WarehouseStore.remove` repurposed as archival

**Status:** accepted

## Context

`DELETE /warehouse/{id}` is described as archiving. The domain model carries `archivedAt`, and the
brief describes a history trail across warehouse replacements. `WarehouseStore` exposes both
`update` and `remove`, a leftover from a hard-delete model.

## Decision

Archiving stamps `archivedAt` and persists through `update`. Archived rows stay in the database and
are excluded from every read path. `remove` is implemented as an alias for archival rather than as a
hard delete.

`ArchiveWarehouseUseCase` calls `update`, not `remove`: the provided skeleton already called
`update`, archival is a field mutation on an existing row, and one archival path is better than two.

## Alternatives considered

- **Hard delete.** Rejected: it destroys the history the replacement feature exists to preserve, and
  contradicts `archivedAt` being in the model at all.
- **Delete `remove` from the port.** Rejected: it is part of the provided interface. Repurposing it
  honestly, with the behaviour documented, beats leaving a method nothing calls or removing a member
  of an interface we were given.

## Consequences

- `getAll`, `findByBusinessUnitCode` and `findActiveById` all filter on `archivedAt is null`. The
  provided `getAll` returned everything, so this working method had to change too.
- A business unit code becomes reusable once its warehouse is archived — which is what makes
  replacement work.
- Archived rows accumulate. At this scale that is the point; at a much larger one it becomes a
  partitioning or retention question.
