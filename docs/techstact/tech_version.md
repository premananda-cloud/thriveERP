# thriveERP — Tech Stack & Versions

> **Status:** Draft — fill in the exact versions from your `pom.xml` / `application.yaml` once confirmed; placeholders marked `TBD`.

---

## Core

| Component | Version | Notes |
|---|---|---|
| Java | TBD (17 or 21 recommended) | LTS release |
| Spring Boot | TBD | check `pom.xml` |
| Build tool | Maven (`mvnw` present) | |
| PostgreSQL | 15 | per `docker-compose.yml` in ARCHITECTURE.md |
| Docker / Docker Compose | TBD | |

## Application Modules (planned, not yet scaffolded as separate modules)

| Module | Depends on |
|---|---|
| core-domain | Java only |
| core-app | core-domain, Spring |
| adapter-persistence | core-domain, Spring Data JPA, PostgreSQL driver |
| adapter-rest | core-app, Spring Web |
| adapter-erp-mock | core-domain |
| adapter-erp-impl | core-domain, iDempiere integration client (TBD — see below) |
| adapter-audit | core-domain, Spring |

*(Currently the repo is a single Spring Boot module — `com.thriveerp.thriveERP`. Splitting into the multi-module layout above is a Sprint 0 task per ARCHITECTURE.md, not done yet.)*

## Migrations

| Component | Version | Notes |
|---|---|---|
| Flyway (or Liquibase) | TBD | pick one — ARCHITECTURE.md assumes Flyway |

## ERP Target

| Component | Version | Notes |
|---|---|---|
| iDempiere | 2026-Sept-8 build (per `idempiere/repository_zips/idempiere-2026-Spt-8.zip`) | Integration method (REST/SOAP/DB) TBD — see DesignLogic.md §4 |

## Testing

| Component | Version | Notes |
|---|---|---|
| JUnit | TBD | |
| Testcontainers (optional) | TBD | useful for adapter-persistence tests against real Postgres |

## Deferred / Not Yet Chosen

- Event bus (Kafka/RabbitMQ) — deferred per ARCHITECTURE.md §10, in-process events for now
- CI tooling
- Frontend framework/version for the Web App layer
- Observability/logging stack

---

*Keep this file in sync with `pom.xml` — when a dependency version changes there, update it here too.*
