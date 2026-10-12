# portala-state — PORTAL-STATE-1 (Application State Machine, MVP / POC)

A small backend service that manages the **lifecycle of `Application` requests** for the
future ecommerce domain. Every request moves through a fixed set of states and every
transition is governed by the state diagram defined in
`README_architecture_design_requirements_todo.md`.

This is the **MVP / proof-of-concept**: a single Spring Boot service, plain JDBC on
PostgreSQL, no GUI (T1). The full requirement traceability is at the bottom.

---

## 1. Tech stack

| Concern        | Choice                                                     | Why |
|----------------|------------------------------------------------------------|-----|
| Language / JVM | **Java 25** (Amazon Corretto 25)                           | Requirement: "Java 25 or later". Records + immutable aggregate fit the model. |
| Build          | **Gradle 9.7.1** (wrapper), `libs.versions.toml` catalog   | Same wrapper/versions with latests. |
| Framework      | **Spring Boot 4.1.1**                                      | Same line as the sibling ECM services; `org.springframework.boot` Gradle plugin. |
| Persistence    | **Spring `JdbcClient`** + `HikariCP` + PostgreSQL driver   |  JDBC only — **no JPA, no Hibernate**. |
| DB migrations  | `schema.sql` via `spring.sql.init`                         | A POC does not need Liquibase; the DDL is idempotent (`CREATE … IF NOT EXISTS`). |
| Tests          | JUnit 5, AssertJ, Mockito, **ArchUnit**, **Testcontainers** (Postgres) | integration + architecture enforcement. |
| JSON           | Jackson 3 (`tools.jackson`, via `spring-boot-starter-jackson`) | Default for Spring Boot 4. |

The machine is **hand-rolled**  while borrowing its concepts (states, events, transitions, guards, actions) .

---

## 2. Domain model

The aggregate is `Application` — an **immutable** snapshot carrying:

`id` (DB identity) · `objId` (UUID, stable across all versions) · `createdDate` · `version`
(0, then `++` per append) · `state` · `name` · `body` · `author` · `reason` · `publicationId`

### State diagram (the `NEW` state was dropped — see §5)

```
      CREATE (entry, guard: name+body present  [F2])
        │
        ▼
     CREATED ────────────────────────────────┐
        │ VERIFY (author set, reason null)   │ DELETE (reason required  )
        ▼                                    ▼
     VERIFIED ───────────────┐          DELETED  (terminal, soft delete  )
        │ ACCEPT (author,     │  REJECT ↗
        ▼  reason null)       │ (reason)
     ACCEPTED ────────────┐   │
        │ PUBLISH          │   │  REJECT → DELETED
        ▼ (author,         │───┘
     PUBLISHED  publicationId  )   (terminal)
```

Forward path: `CREATED → VERIFIED → ACCEPTED → PUBLISHED`. `DELETED` is reachable from
`CREATED` (DELETE) and from `VERIFIED` / `ACCEPTED` (REJECT). `PUBLISHED` and `DELETED` are
terminal and accept no further event  .

### Audit log = append-only history 

There is **no UPDATE and no DELETE** on the table. Each successful command inserts one new
row with the same `objId` and `version + 1` (`uq_application_obj_version`). The whole history
of a request is therefore `SELECT … WHERE obj_id = ? ORDER BY version`, exposed at
`GET /api/applications/{objId}/history`.

### Optimistic locking — manual, application side

The service reads the newest row, fires the transition to `version N+1`, and appends.
`GET`/`PUT`/event requests carry an **`expectedVersion`**; if it is not the current newest
version the call is refused with **412**. Under real concurrency the `unique (obj_id, version)`
constraint makes the losing INSERT fail, which the adapter also translates to a 412.
**This is enough for the expected write volume** (see §5 for the pessimistic-locking escalation path).

---

## 3. Architecture (hexagonal / ports & adapters approach to design.

Everything lives under the single root package `pl.portala.statemachine` , split into
sub-packages by role:

```
domain/          pure business: Application, enums, ApplicationStateMachine, guards, exceptions
                 (NO Spring, NO SQL — enforced by ArchUnit)
port/incoming/   driving port: ApplicationUseCases
port/outgoing/   driven ports:  ApplicationRepository, ApplicationQueryRepository, PublicationIdGenerator
application/     use-case implementation: ApplicationWorkflowService
adapter/incoming/web/      REST driving adapter + DTOs + GlobalExceptionHandler 
adapter/outgoing/jdbc/     JDBC driven adapters (write repo, read repo, sequence generator, row mapper)
config/          Spring beans (state machine, Clock, JdbcClient)
```

CQRS-lite  : the **write** flow (`ApplicationRepository`) only appends aggregate versions;
the **read** flow (`ApplicationQueryRepository`) projects the newest version per `objId` for
browsing, fully independent of the write stack. The layering is locked down by
`ArchitectureTest` (ArchUnit).

`ApplicationStateMachine` is a transition table of `(from, event) → to`, each with an **action**
(merge the incoming author / reason / publicationId) and a **guard** (the AND-clauses of the
diagram). `fire()` returns a new immutable aggregate at `version + 1` or throws
`IllegalStateTransitionException` (409) / `StateGuardViolationException` (409).

---

## 4. REST API (T1)

Base path `/api/applications`. Responses are the current snapshot (`ApplicationResponse`).

| Method & path                        | Purpose                                   | 
|--------------------------------------|-------------------------------------------|
| `POST /`                             | Create (name+body mandatory, author opt.) | 
| `GET /{objId}`                       | Current snapshot                          |
| `GET /{objId}/history`               | Full append-only history                  |
| `PUT /{objId}/content`               | Edit name/body (CREATED/VERIFIED only)    |
| `POST /{objId}/events`               | Fire VERIFY/ACCEPT/PUBLISH/DELETE/REJECT  | 
| `GET /?name=&state=&page=&size=`     | List, paginated + filters                 | 

**Event body**: `{ "event": "VERIFY", "author": "...", "reason": "FINISH_ON_DELETE", "expectedVersion": 0 }`.
`reason` is only relevant for DELETE/REJECT; `publicationId` is never sent by the client — it is
assigned server-side on PUBLISH (F5).

**Browsing   decision**: offset pagination, **page is 0-based, default size 10**,
name filter is **case-insensitive `LIKE %term%`**, ordering `created_date DESC, objId`.
**Soft-delete   decision**: `DELETED` requests are **hidden** from the default list and appear
only when `state=DELETED` is requested explicitly.

**Error contract (R6)** — RFC-7807 `application/problem+json` with a `code` property:

| HTTP | code | when |
|------|------|------|
| 400 | `REQUEST_VALIDATION_FAILED` / `INVALID_ARGUMENT` / `MALFORMED_REQUEST` | bean validation, unknown event/state, bad JSON |
| 404 | `APPLICATION_NOT_FOUND` | unknown `objId` |
| 409 | `ILLEGAL_STATE_TRANSITION` / `GUARD_VIOLATION` | event not in diagram, or a guard/content rule failed  |
| 412 | `VERSION_CONFLICT` | `expectedVersion` is stale (optimistic lock) |
| 500 | `INTERNAL_ERROR` | anything unexpected |

Note: business-rule violations are returned as **409**, following the ECM house convention,
distinguished by `code` rather than by exotic status codes.

---

## 5. Assumptions & decisions (T4)

- ** `CREATED` is the initial state (created with a validated name+body).
  Keeping `NEW` would add a persisted row/event with no business meaning; the append-only log is
  cleaner starting at `CREATED`.
- **Single `Application` class/table (MVP).** The `Application` / `ApplicationData` split described
  in the doc is deferred; with the append-only table the content already lives per-version, and the
  split can be introduced later without changing the REST contract.
- **Reason is supplied by the client** for DELETE/REJECT (not auto-derived), so guard F4 is
  genuinely testable. Enum values match the doc: `FINISH_ON_DELETE`, `FINISH_ON_REJECTED`.
- **author required** on the forward and delete/reject transitions ; optional at creation.
- **publicationId via a plain PostgreSQL sequence read from Java.** Consequence: `nextval` is
  non-transactional, so a rolled-back publish leaves a **gap** — acceptable because ids only need
  to be unique, not contiguous. (Alternative rejected: a dedicated counter table + DB function —
  more moving parts for a POC.)
- **Optimistic locking is sufficient** for the predicted concurrency. If contention ever appears,
  escalate to `SELECT … FOR UPDATE SKIP LOCKED` or a PG advisory lock inside `append` — the port
  boundary keeps that change local to the JDBC adapter.

---

## 6. Build & run (D5)

Requirements: **JDK 25** and **Docker** (the integration tests and a real run need PostgreSQL via
Testcontainers / a local server).

```bash
export JAVA_HOME=/path/to/corretto-25      # e.g. ~/.sdkman/candidates/java/25.0.4-amzn

# run the whole test suite (unit + architecture + Testcontainers IT)
./gradlew clean test

# start the service against a local PostgreSQL (create the DB first, or override the URL)
./gradlew bootRun \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/portala_state \
  --spring.datasource.username=portala \
  --spring.datasource.password=portala
# the append-only schema (schema.sql) is created automatically on startup

curl -s -X POST localhost:8080/api/applications \
  -H 'Content-Type: application/json' \
  -d '{"name":"my request","body":"content"}'
```

The database itself is **not** part of the deliverable ; any PostgreSQL 14+ works,
`postgres:18.6` is used in tests for parity with `portala-ecm` / `portala-ctp`.

> **Testcontainers note:** run Gradle builds **one at a time**. Concurrent builds race on the
> shared Postgres container and produce spurious failures (same caveat as the ECM/CTP repos).

---

## 7. Deliverables map 

- **D1** — this README.
- **D2** — Gradle sources + metadata (`build.gradle`, `settings.gradle`, `gradle/libs.versions.toml`, wrapper).
- **D3** — tests: `ApplicationStateMachineTest` (A3.1 transitions/guards/actions),
  `ApplicationWorkflowServiceTest` (A3.1, mocked ports), `ApplicationRestLifecycleIntegrationTest` 
- **D4** — library rationale in §1.
- **D5** — build/run guide in §6.
- **D6** — `.gitignore` excludes build output, `.gradle`, IDE and node files; only source is tracked.

---

## 8. Requirement traceability

| ID | Where implemented |
|----|-------------------|
| 1 | `ApplicationStateMachine.fire` + `IllegalStateTransitionException` |
| 2 | `Application.start` guard + `@NotBlank` on create |
| 3 | `Application.isContentEditable` + `ContentNotEditableException` |
| 4 | DELETE/REJECT guards require `reason` |
| 5 | PUBLISH assigns `publicationId` (`PublicationIdGenerator`) |
| 6 | append-only `application` table + `/history` |
| 7 | `GET /api/applications` offset paging + name/state filters |
| T1 | `ApplicationController` (REST only) |
| T2 | PostgreSQL via `JdbcClient`; DB not shipped |
| A4/A5 | JDBC-only, domain free of Spring/SQL |
| A6 | separate write / query repositories (CQRS-lite) |
| A7  | single root package `pl.portala.statemachine` |
| R1/R2 | ports & adapters + ArchUnit, soft delete, list filter, author, documented paging, global handler, hand-rolled machine |
