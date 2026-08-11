# ADR-0006: MapStruct evaluated and rejected

**Status:** accepted

## Context

The codebase maps between three representations: the generated API bean, the domain model, and the
JPA entity. The stated criterion for adopting a mapping library was that it must reduce code
substantially.

## Decision

Hand-write the mappers. Do not adopt MapStruct. Do not adopt Lombok either.

## Alternatives considered

- **MapStruct.** Measured rather than argued: four mappings, roughly 37 hand-written lines against
  roughly 30 generated, plus build configuration and an extra extension. Break-even at best, so it
  fails the stated criterion.

  The genuine argument for MapStruct here is `unmappedTargetPolicy = ERROR`, which would have caught
  the dropped-`id` defect at compile time. That is a correctness argument, not a line-count one, and
  it is worth recording that the criterion — not the tool — is what decided this.
- **Lombok.** Rejected: the entities are public-field POJOs, and Quarkus plus annotation processors
  is friction for no gain across four small classes.

## Consequences

- Mapping bugs are caught by tests rather than by the compiler. The specific bug that motivates this
  trade-off — an unmapped `id` — now has a test asserting the API returns one.
- Both mapping directions are written explicitly, including the inbound one the baseline lacked.
