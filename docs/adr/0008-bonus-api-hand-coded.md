# ADR-0008: Hand-code the fulfilment API rather than generating it from a contract

**Status:** accepted

## Context

The warehouse API is generated from an OpenAPI document. The bonus fulfilment API is new, internal,
and has no consumers yet.

## Decision

Hand-write `FulfilmentResource`. Do not add it to the contract or the generator.

## Alternatives considered

- **Extend `warehouse-openapi.yaml` and generate it too, for consistency.** Rejected, and this is
  the point rather than a shortcut: contract-first earns its ceremony when a published contract is
  what other systems depend on. Applying the same ceremony to an unproven internal endpoint is
  governance with nothing to govern.

## Consequences

- The repository contains one of each approach, which makes the trade-off in Question 2
  demonstrable rather than asserted: the choice is per consumer, not per codebase.
- If the fulfilment API acquires external consumers, the decision should be revisited — a contract
  written after the shape has settled is cheaper and better than one guessed up front.
- The error shape is kept identical to the warehouse API's by a mapper of its own, so hand-coding
  costs no consistency.
