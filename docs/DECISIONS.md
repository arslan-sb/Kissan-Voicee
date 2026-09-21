# Architecture Decision Log

Short records of choices that were not obvious, and what they cost.

---

## ADR-001 — Spring Boot 3.5.x, not 4.1.x

**Date:** 2026-09-21 · **Status:** Accepted

**Context.** Spring Initializr no longer generates Spring Boot 3.5.x; the current
line is 4.1.x on Spring Framework 7. The initial scaffold was generated on 4.1.1
and compiled cleanly.

**Decision.** Pin the parent to `3.5.14`.

**Why.** Two libraries this platform depends on have no Spring Boot 4 release on
Maven Central:

| Library | Latest on Central | Targets |
|---|---|---|
| `springdoc-openapi-starter-webmvc-ui` | 2.8.6 | Spring Boot 3.x only |
| `resilience4j-spring-boot*` | `resilience4j-spring-boot3` 2.3.0 | Spring Boot 3.x |

springdoc provides the OpenAPI contract and Swagger UI, which is the primary demo
surface. Resilience4j provides the retry and circuit-breaker behaviour in the CRM
integration facade, which is the most load-bearing part of the design. Neither is
optional, and neither can be resolved on Boot 4 today.

Spring Boot 4 also restructures the starters (`spring-boot-starter-web` →
`spring-boot-starter-webmvc`, per-starter `-test` companions, Testcontainers
classes moved to `org.testcontainers.postgresql`), so the migration is not a
one-line parent bump.

**Consequence.** The repo runs one minor line behind current. Upgrading is a
contained piece of work once springdoc 3.x and a `resilience4j-spring-boot4`
artifact are published: bump the parent, rename the starters, fix the
Testcontainers imports. Tracked as a follow-up rather than done under a deadline.
