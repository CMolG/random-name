# ADR-0007: Scope mutation testing to the domain layer

**Status:** accepted

## Context

Line coverage says which lines ran, not whether anything would have noticed had they misbehaved. PIT
answers the second question by mutating bytecode and checking whether a test fails.

## Decision

Run PIT in a `mutation` profile, targeting `warehouses.domain.*` and `location.*`, and driving it
only from the plain-JUnit tests. `@QuarkusTest` and `*IT` classes are excluded.

## Alternatives considered

- **Mutating the whole application, including the REST layer.** Rejected: PIT rewrites bytecode and
  so does Quarkus, which makes the combination unreliable. The domain is also where the business
  rules live, so it is where a surviving mutant carries information.
- **JaCoCo line coverage instead, or as well.** Rejected. Mutation score strictly dominates line
  coverage as a signal, and publishing both invites anchoring on the weaker number.

## Consequences

- The rule layer is measured strictly; the adapters are covered by REST and integration tests
  instead.
- The first run scored 84% with 7 survivors, all of which turned out to be real gaps — untested
  boundaries, an unread error message, and a test double that made persistence unobservable. After
  triage: 44 mutants, 44 killed. Line coverage was 99% before and after, which is the argument for
  this ADR in one number.
- The threshold stays at 85% and was not lowered to fit the result.
