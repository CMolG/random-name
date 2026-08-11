# ADR-0009: Fulfilment count rules are check-then-act; the race is documented, not closed

**Status:** accepted

## Context

The three fulfilment limits are counting rules: at most 2 warehouses per product per store, 3
warehouses per store, 5 products per warehouse. They are enforced by reading the current counts and
then inserting.

## Decision

Enforce them in the service with a read followed by an insert, under the default isolation level,
and state the limitation rather than implying a guarantee that is not there.

## Alternatives considered

- **`SERIALIZABLE` isolation, or a locking read on the store and warehouse rows.** Correct, and
  disproportionate for this feature at this scale. It is the fix if the endpoint ever becomes hot.
- **A database constraint per rule.** Not expressible: these are counts across rows, not properties
  of one row.

## Consequences

- Two concurrent requests can each observe a count of 2 and both insert, leaving 3 warehouses for a
  product in a store. The window is small and the consequence is a limit exceeded by one, not
  corruption.
- The unique constraint on `(store_id, product_id, warehouse_id)` **is** enforced by the database, so
  the duplicate case holds under concurrency even though the count rules do not.
- Recorded here so the gap is a known trade-off rather than an assumption someone later relies on.
