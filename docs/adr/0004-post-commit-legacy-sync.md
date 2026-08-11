# ADR-0004: Propagate store changes to the legacy system after commit, via a CDI event

**Status:** accepted

## Context

`StoreResource` called `LegacyStoreManagerGateway` inside the transaction. A rollback after that call
leaves the legacy system believing in a store the database never kept — and `Store.name` is unique,
so rollbacks are reachable from ordinary input.

## Decision

The resource fires a `StoreChangedEvent`; `LegacyStoreSyncObserver` observes it at
`TransactionPhase.AFTER_SUCCESS` and calls the gateway. The event is an immutable snapshot of the
committed values, captured inside the transaction.

A snapshot rather than the entity, because observers run after the persistence context is gone and a
managed entity read there is a detached instance.

## Alternatives considered

- **Call the gateway after the transactional method returns.** Rejected: it spreads transaction
  awareness through the resource and does not compose with future callers.
- **A transactional outbox with a relay.** Correct for exactly-once delivery, and out of scope here.
  See consequences.

## Consequences

- A rolled-back transaction propagates nothing. There is a test that asserts precisely this.
- Delivery is **at-most-once**: if the process dies between commit and the gateway call, the update
  is lost. An outbox table written in the same transaction, plus a relay, would close it. That is a
  deliberate scope decision, not an oversight.
- `LegacyStoreManagerGateway` stays byte-identical to the baseline.
