# ADR-0002: `{id}` in the warehouse API is the numeric database id

**Status:** accepted

## Context

`GET|DELETE /warehouse/{id}` types `id` as `string` in the OpenAPI contract, while the warehouse
also carries a `businessUnitCode` that reads like a natural key. Either could be meant.

## Decision

`{id}` is the numeric database id.

The provided `WarehouseEndpointIT.testSimpleCheckingArchivingWarehouses`, commented out in the
baseline, archives `/1` and then asserts `ZWOLLE-001` is absent. Row 1 of `import.sql` is
`MWH.001 @ ZWOLLE-001`. The provided test therefore answers the question, and the same test proves
`listAllWarehousesUnits` must exclude archived warehouses.

## Alternatives considered

- **`{id}` is the business unit code.** Rejected: it contradicts the only executable evidence in the
  repository about the intended behaviour.

## Consequences

- The domain model gains an `id`, and the response mapper must populate it — the provided mapper did
  not, so the API could never return the identifier its own schema advertises.
- The port gains a lookup by id (see ADR-0003 and the `findActiveById` naming note).
- Replacement still keys on `businessUnitCode`, because its path says so.
