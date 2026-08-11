# ADR-0005: Keep the provided generator; split validation between the engine and the use cases

**Status:** accepted (supersedes two earlier recommendations, recorded below)

## Context

The warehouse API is generated from `warehouse-openapi.yaml` by
`quarkus-openapi-generator-server` 2.4.7. The contract declares `required: true` on the request
body, but nothing enforced it: `hibernate-validator` appeared in the dependency tree only as
`quarkus-hibernate-validator-spi:test`, so the generated `@NotNull` compiled and did nothing.

## Decision

1. Keep the provided generator and the contract-first approach.
2. Add `quarkus-hibernate-validator`, giving the generated `@NotNull` an engine for the first time.
3. Remove the redeclared `@NotNull` from `WarehouseResourceImpl`, which Jakarta Validation forbids on
   an overriding method and which throws `ConstraintDeclarationException` once an engine exists.
4. Keep field-level rules in the use cases, next to the business rules they are inseparable from.
5. Put no `minLength` / `maxLength` / `x-codegen-annotations` in the YAML.

## Two recommendations that were wrong, and how they were caught

- **Replacing the generator with `openapi-generator-maven-plugin` was recommended and rejected.** It
  would leave the Quarkus codegen lifecycle and unpin the version from the platform.
- **`x-codegen-annotations` was asserted as the mechanism for field constraints without being run.**
  Challenged rather than accepted, then tested: property-level annotations are silently ignored, and
  schema-level ones are applied to the class, where they are inert for fields. Seven configurations
  across two extension versions produced field constraints in none of them, and `@Valid` is never
  generated at all. Extension 2.9.0 additionally fails to compile here, because it emits MicroProfile
  OpenAPI annotations and `quarkus-smallrye-openapi` is not a dependency — so "revisit after a
  version bump" would have been inaccurate advice.

## Consequences

- Null bodies are rejected as the contract always promised. There is a test asserting the 400, which
  only passes if both the dependency and the annotation removal are done — one without the other
  either breaks startup or leaves the body unvalidated.
- Constraints that the generator cannot honour are absent from the YAML, rather than present as
  documentation masquerading as enforcement.
- Quarkus emits its own `ViolationReport` shape for validation failures, so a mapper for
  `ResteasyReactiveViolationException` restores the house error shape.
