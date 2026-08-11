# Questions

Here we have 3 questions related to the code base for you to answer. It is not about right or wrong, but more about what's the reasoning behind your decisions.

1. In this code base, we have some different implementation strategies when it comes to database access layer and manipulation. If you would maintain this code base, would you refactor any of those? Why?

**Answer:**
```txt
There are three strategies here, and they are not three ways of doing the same thing.

  - Store is an active-record PanacheEntity: the entity persists itself.
  - Product uses a Panache repository: persistence sits beside the entity.
  - Warehouse goes through a port, WarehouseStore, implemented by a Panache repository,
    with use cases holding the rules.

My position is: refactor by risk, not for symmetry. I would leave Store and Product
alone and I did leave them alone in this submission, so the answer is visible in the
diff rather than only asserted here.

The reason is that the three strategies are not arbitrary variation, they track how
much business logic each entity carries. Product and Store are CRUD. They have no
invariants worth protecting: any name, any stock. Wrapping them in ports and use
cases would add roughly fifteen classes and change no behaviour. Warehouse is the
opposite. It has uniqueness of the active business unit code, location existence,
a warehouse count per location, a summed capacity per location, stock within
capacity, and a replacement protocol that has to archive and create atomically.
Those rules need somewhere to live that is not a JAX-RS resource and not an entity,
and they need to be testable without starting a database. That is what the port buys,
and the payoff is concrete: the entire rule layer is tested by plain JUnit in
milliseconds, which is what made mutation testing on it practical.

So the mixed strategy is defensible as it stands. What I would change is different,
and more urgent:

1. Schema management. quarkus.hibernate-orm.database.generation=drop-and-create with
   import.sql is right for an assignment and unusable in production, where it means
   every restart destroys the data. This should become versioned migrations (Flyway
   or Liquibase) before anything is deployed. That is the single highest-risk item in
   the persistence layer, and it is not a strategy question at all. It is also the
   change that makes the other two safe to do incrementally, because it gives you a
   way to evolve the schema without recreating it.

2. Reads that cannot skip the rule layer. WarehouseRepository.getAll originally
   returned listAll(), archived rows included. That is exactly the kind of defect a
   port is supposed to prevent and did not, because the filter lived nowhere. I
   changed it to filter on archivedAt and made "active only" part of the port's
   contract by naming the id lookup findActiveById. Naming the invariant in the
   method is cheaper than documenting it and more durable than remembering it.

3. Transaction boundaries. @Transactional currently sits on the repository methods and
   on the replace use case. The repository is the wrong place for it in principle,
   because a transaction is a unit of work, not a unit of storage. The reason it is
   still there is that the use cases would otherwise depend on jakarta.transaction,
   which the port is meant to keep out of the domain. The honest fix is a small
   transactional decorator at the application boundary. I would do it the moment a
   second multi-step operation appears; with one, replacement, the current shape is
   still readable.

The general principle: unifying persistence strategies is a refactor with a real cost
and no user-visible benefit. I would spend that budget on the schema migration
problem, which has a real failure mode behind it. If I did unify, it would be
downward, moving Store's active-record calls behind a repository, because
active-record makes an entity depend on its own persistence and that is what makes
Store hardest to test in isolation. But that is a preference, and preferences are not
a good enough reason to touch working code.
```
----
2. When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded directly everything. What would be your thoughts about what are the pros and cons of each approach and what would be your choice?

**Answer:**
```txt
My choice is both, chosen per consumer rather than per codebase, and this submission
does exactly that: the warehouse API stayed contract-first, and the bonus fulfilment
API is hand-written. Having one of each makes the trade-off demonstrable.

Contract-first, as practised here.

The strongest thing it gave me was an ordering discipline. The warehouse API needed
to return 409 for a duplicate business unit code and 422 for a client-supplied id.
Neither was in warehouse-openapi.yaml. So the contract changed first, in its own step,
and the code followed. If I had returned a 409 the document did not describe, the
claim that the YAML is the source of truth would have been false, and the next person
reading it would have been misled by their own documentation. Contract-first is
mostly valuable as a habit that makes that mistake awkward rather than easy.

The cons I hit are more interesting than the textbook ones, because I measured them.

The contract said required: true on the request body, and the generated interface
carried @NotNull, and it did nothing at all. jakarta.validation-api was on the compile
classpath transitively, so it compiled; hibernate-validator was present only as a test
scoped SPI artifact, so at runtime there was no engine. The contract's promise was
silently unenforced. That is the failure mode specific to generated code: it looks
like enforcement, so nobody checks.

Then I tried to push field constraints through the generator and could not. I ran
seven configurations across two extension versions:
required/minLength/maxLength in the YAML, use-bean-validation under three different
property prefixes, the openapitools generator switch, property-level and schema-level
x-codegen-annotations. None produced a single field annotation. Property-level
x-codegen-annotations is silently ignored; schema-level lands on the class, where it
is inert for fields. @Valid is never generated at all, and I cannot add it in my
implementation either, because Jakarta Validation prohibits adding @Valid or parameter
constraints on an overriding method. Extension 2.9.0 does not compile in this project,
because it emits MicroProfile OpenAPI annotations and quarkus-smallrye-openapi is not a
dependency, so "revisit after a version bump" would have been wrong advice. The
capability lives in the client extension, which wraps a different generator entirely.

Two lessons from that, and they are the real cons. First, unknown codegen properties
are ignored without warning, so a misconfiguration produces no error and no output
change: you find it only by diffing the generated source, which is exactly what almost
nobody does. Second, you inherit the generator's expressiveness, and its ceiling is
lower than the spec's. What the contract can say and what the code will enforce are
two different sets, and the gap is invisible.

That is why field-level rules live in the use cases in this codebase, next to the
business rules they are inseparable from. A blank business unit code and a duplicate
business unit code are the same kind of rejection to a caller; splitting them across
two layers to satisfy a diagram would buy nothing. And I deliberately did not put
minLength or maxLength into the YAML, because constraints the generator cannot honour
are documentation masquerading as enforcement, which is worse than no documentation.

Hand-coded, as practised here.

For the bonus fulfilment API I wrote the endpoint directly. It is new, internal, and
has no consumers yet. Contract-first earns its ceremony when a published contract is
what other systems depend on and changing it needs to be a visible act. Applying the
same ceremony to an endpoint whose shape I was still deciding would have been
governance with nothing to govern, and would have made every iteration a two-step
edit. The cost is real: nothing stops the fulfilment API drifting from any document
describing it, because no such document exists. If it acquires external consumers I
would write the contract then, once the shape has settled, which produces a better
contract than one guessed up front.

So the rule I would apply: generate from a contract when the contract is an agreement
with someone outside your deployment unit, hand-write when it is not, and be willing
to move an endpoint from the second category to the first when its audience changes.
Uniformity is not the goal; knowing which one you are in is.

Scope decision, recorded here rather than quietly worked around:

DELETE /store/{id} does not notify the legacy system. The legacy gateway exposes only
create and update operations, so a deleted store leaves an orphan record in the legacy
register. Closing this would require either a change to the legacy contract, which is
outside my control, or an outbox with periodic reconciliation to flag records with no
counterpart. I scoped it out and applied the post-commit guarantee only to the
operations the gateway actually supports, rather than extending provided code to make
the problem disappear. Related: the propagation I did implement is at-most-once. If
the process dies between commit and the gateway call, the update is lost. A
transactional outbox written in the same transaction, plus a relay, is the fix, and it
is a bigger piece of work than this assignment calls for. Both are in ADR-0004.
```
----
3. Given the need to balance thorough testing with time and resource constraints, how would you prioritize and implement tests for this project? Which types of tests would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
```txt
Priorities, in the order I actually worked.

First, make the tests able to run at all. Before anything else, mvn verify in this
project passed without executing WarehouseEndpointIT: maven-failsafe-plugin was
declared only inside the native profile, and Surefire's default includes do not match
*IT. A green build that silently skips a test is worse than a red one, because it
actively reports safety. Fixing that came before writing a single new test, and CI now
asserts the Failsafe report for that class exists rather than trusting the exit code.
The same category of problem: mvnw was not executable, so CI could not have run either.

Second, the business rules, tested without a container. Everything in the warehouse
domain — uniqueness, location limits, summed capacity, stock within capacity, the
replacement protocol — is plain JUnit against in-memory implementations of the two
ports. No Quarkus, no database, milliseconds per test. This is where I would always
spend first, because these tests are the ones that stay fast enough to run on every
keystroke and because a rule that can only be tested through HTTP is a rule in the
wrong place.

Third, one @QuarkusTest layer per adapter, for the things unit tests structurally
cannot reach: status codes, the error body shape, whether a null body is actually
rejected, whether a transaction rollback really suppresses the legacy call, and
whether @ResponseStatus(201) is honoured. That last one is a good example of why this
layer is not optional: @ResponseStatus is read only from a method carrying its own
JAX-RS annotation, so on an inherited @POST it is silently ignored and the response is
200, with no error anywhere. No unit test can see that. Similarly, the rollback test
is the only place that proves the archival in a failed replacement is undone, because
the in-memory store has no transaction to roll back.

Fourth, one integration test against the packaged application, to catch what only
appears when the real artifact starts against a real database.

The shape is the usual pyramid, but I would defend the proportions from the failure
modes rather than the diagram: most of the risk here is in rules, so most of the tests
are rule tests.

Keeping coverage effective over time.

Line coverage is the wrong metric and I did not measure it. It tells you a line
executed, not that anything would have noticed if the line were wrong. This project
uses mutation testing instead, and the numbers make the argument better than I can.

The domain suite was green and every rule had a test. PIT scored it 84 percent:
44 mutants, 7 alive. Each survivor was a sentence the tests could not finish.

  - stock < 0 mutated to <= 0 survived: nothing ever created a warehouse with zero
    stock, the state every new warehouse begins in.
  - stock > capacity mutated to >= survived: nothing ever created a full warehouse.
  - The location filter forced to always-true survived: every location test used one
    location, so "limits are per location" was never actually asserted.
  - maxCapacity - used mutated to + survived: the remaining-capacity number in the
    error message was never read by any test.
  - Deleting the blank-location check survived: a blank location still failed, but via
    the resolver, reported as "location    does not exist". Two different client
    mistakes collapsing into one useless message.
  - Deleting warehouseStore.update in archive survived, which was the most useful
    finding: not a gap in the tests but a defect in the test double. It held
    warehouses by reference, so archiving was visible whether or not anything was
    persisted. Against a database it would not be. The double now counts the calls.

Six new tests later: 44 mutants, 44 killed. Line coverage was 99 percent before the
triage and 99 percent after. It moved by nothing while the suite became materially
stronger, which is the entire argument for measuring mutants rather than lines in one
number.

So, concretely, to keep it effective:

  - A mutation threshold in CI on the rule layer, not a line-coverage threshold. Fail
    the build below it, and triage survivors rather than lowering the bar. Every
    survivor is either a missing test, a test worth deleting because it asserts
    nothing, or a genuinely equivalent mutant worth recording.
  - Test rules at their boundaries, not just their middles. "Rejects 3 warehouses"
    passes just as happily against an off-by-one; "accepts 2 and rejects 3" does not.
    I checked this on the fulfilment limits by flipping each >= to > and confirming
    exactly the three boundary tests failed.
  - Write the test first and watch it fail for the reason you expect. Every use case
    here was committed red then green, so the history itself is the evidence.
  - Keep the fast layer fast. The moment the rule tests need a container, they stop
    being run constantly and start being run in CI, and the feedback loop that makes
    them valuable is gone.
  - Treat a flaky or order-dependent test as a defect in the test. Two tests here
    failed because @QuarkusTest shares one database across a class with no rollback,
    and fixtures from different classes competed for the same location's capacity. The
    fix was isolating the fixtures, not retrying the test.
```
