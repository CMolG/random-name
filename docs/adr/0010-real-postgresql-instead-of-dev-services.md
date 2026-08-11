# ADR-0010: Disable Dev Services; run tests against a real PostgreSQL on port 15432

**Status:** accepted

## Context

Quarkus Dev Services would normally start a database for tests automatically. On the development
machine it cannot: Docker Desktop 29.2.0 answers `/info` with HTTP 400 to the `docker-java` client
bundled with Quarkus 3.13.3, so every Testcontainers strategy fails and the build aborts before any
test runs. This was root-caused by running it, not inferred from the symptom.

## Decision

Set `quarkus.devservices.enabled=false` and point the tests at a PostgreSQL container started by
`docker-compose.yml` on port **15432**, with the `quarkus_test` credentials.

Port and credentials are not arbitrary: they are what the provided `java-assignment/README.md`
already documents and what `%prod.quarkus.datasource.jdbc.url` already points at, which is why the
packaged-app integration test needs no configuration of its own.

The test datasource lives in `src/test/resources/application.properties`, so the provided
`src/main/resources/application.properties` stays byte-identical to the baseline.

## Alternatives considered

- **Downgrade or reconfigure Docker Desktop.** Rejected: it fixes one machine and leaves the project
  depending on a version relationship no one can see from the repository.
- **Upgrade Quarkus to get a newer `docker-java`.** Out of scope for an assignment whose platform
  version is part of the given code.
- **In-memory H2 for tests.** Rejected: it would test a different database from the one the
  application runs on, and the integration test exists precisely to exercise the real thing.

## Consequences

- `docker compose up -d` is a documented prerequisite for running the tests, locally and in the
  README.
- CI uses a service container with the same port and credentials, so the two environments agree.
- The failure mode is now legible: if the database is not running, the tests fail to connect rather
  than failing inside a container-discovery stack.
