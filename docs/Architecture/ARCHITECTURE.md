# thriveERP — Pluggable Appointment Engine (PAE)
### Architecture v1.0

> **Status:** Approved for initial development
> **Goal:** A simple, modular, locally-runnable appointment system with a pluggable connector to a legacy/local ERP.
> **Philosophy:** Core domain is sacred. Infrastructure is swappable. Don't overthink day one.

---

## 1. Core Principles

- **KISS** — build the core; don't solve edge cases we don't have yet.
- **Pluggable by design** — the ERP integration is an *interface*, not a hardcoded dependency. Swap it for a mock, a different client's ERP, or a future upgrade without touching business logic.
- **Local first** — the engine and the ERP connector run on-premises, alongside the legacy ERP.
- **Containerized** — everything runs under Docker for consistent dev/staging/prod environments.
- **Template, not a product** — this should be reusable as a base for future clients/instances, not a one-off standalone build.

---

## 2. High-Level Architecture

```
Web App (UI)
    │  HTTP/REST
    ▼
adapter-rest        (REST controllers, DTOs)
    ▼
core-app            (orchestration / use cases)
    ▼
core-domain         (business rules: Appointment, Payment, reschedule counter,
                      status machine — defines EngineConnectorPort)
    │
    ├──▶ adapter-persistence  ──▶ PostgreSQL
    ├──▶ adapter-erp-impl     ──▶ Local ERP (SOAP / REST / SQL / files)
    └──▶ adapter-audit        ──▶ audit_log table
```

**Core domain depends on nothing.** Everything else depends on the core, never the reverse (hexagonal / ports & adapters).

---

## 3. Module Structure (multi-module Maven/Gradle)

| Module | Responsibility | Owner |
|---|---|---|
| `core-domain` | Pure Java. Business rules (reschedule counter, status transitions). Defines `EngineConnectorPort`. Zero framework dependencies. | Core team |
| `core-app` | Orchestration layer (Spring service). Implements use cases. Knows nothing about Postgres or the ERP. | Core team |
| `adapter-persistence` | Implements the persistence port. Maps domain objects ↔ PostgreSQL (JPA). Handles `version` for optimistic locking. | Backend/infra |
| `adapter-rest` | REST controllers + DTOs. Shields the domain from API/frontend changes. | Web team |
| `adapter-erp-impl` | Concrete implementation of `EngineConnectorPort` for the real local ERP (SOAP/SQL/files). Runs locally, next to the ERP. | ERP integration team |
| `adapter-erp-mock` | No-op / logging implementation of `EngineConnectorPort`, used in dev so the app runs without the ERP available. | Core team |
| `adapter-audit` | Listens to domain events, writes to `audit_log` (how many times / when / how). | Analytics/data |

This split lets each concern be worked on and swapped independently.

---

## 4. The Critical Contract — `EngineConnectorPort`

Defined once in `core-domain`. Everyone else depends on it; nobody depends on a specific implementation.

```java
public interface EngineConnectorPort {

    // Push appointment + payment to the ERP (e.g. invoice/order creation)
    ErpResponse pushAppointment(AppointmentDomain appointment, PaymentDomain payment);

    // Optional: pull resource/staff availability if the ERP owns that data
    ResourceAvailability pullResourceAvailability(String resourceId, LocalDateTime time);
}
```

- The **core team** codes against this interface only.
- The **ERP team** ships `adapter-erp-impl`, translating domain objects into whatever the legacy ERP expects.
- Result: the whole appointment flow can be developed and tested via `adapter-erp-mock` with the ERP completely offline.

---

## 5. Data Model (PostgreSQL)

A single Postgres instance backs the core. The ERP keeps its own data — we only sync what we need through the adapter.

**`appointments`**
| Field | Type | Notes |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | customer reference |
| service_id | UUID | service booked |
| scheduled_at | TIMESTAMP | |
| duration_min | INT | |
| status | ENUM | pending, confirmed, in_progress, completed, canceled, no_show |
| reschedule_count | INT | incremented on every reschedule |
| created_at | TIMESTAMP | when first created |
| updated_at | TIMESTAMP | when last changed |
| last_action | VARCHAR | how — web / mobile / call_center / api |
| version | INT | optimistic locking |

**`payments`**
| Field | Type | Notes |
|---|---|---|
| id | UUID | PK |
| appointment_id | UUID | FK |
| amount | DECIMAL(10,2) | |
| currency | CHAR(3) | |
| status | ENUM | pending, paid, failed, refunded |
| payment_method | VARCHAR | credit_card, cash, invoice |
| paid_at | TIMESTAMP | |
| created_at | TIMESTAMP | |

**`audit_log`**
| Field | Type | Notes |
|---|---|---|
| id | BIGINT | PK, auto-increment |
| appointment_id | UUID | reference |
| action | VARCHAR | created, rescheduled, canceled, status_changed, etc. |
| old_value | JSON | previous state |
| new_value | JSON | new state |
| actor | UUID | who performed the action |
| source | VARCHAR | how — channel |
| timestamp | TIMESTAMP | when |

This trio answers *how many times*, *when*, and *how* for any appointment without bloating the main table.

---

## 6. How We Track "how many times / when / how"

- **Reschedule count** → `appointments.reschedule_count`, incremented in the domain service before save.
- **When / how** → `appointments.updated_at` and `appointments.last_action`.
- **Full history** → every state change fires a domain event → `adapter-audit` appends a row to `audit_log`. This supports questions like "how many times did this appointment change status in the last week?" without touching the hot table.

---

## 7. Docker & Deployment Strategy

We use **Docker Compose** to run the stack.

**Container vs. single package — decision for now:**
Start with **one Spring Boot fat JAR** containing all modules, running in **one container**, alongside a Postgres container. Use a Spring profile to pick the ERP adapter at startup:

- `erp-mock` — for local dev, no real ERP needed.
- `erp-live` — loads `adapter-erp-impl`, talks to the real local ERP.

Splitting the ERP adapter into its own container/sidecar is a later step, once the ERP integration is heavy enough or needs its own lifecycle — the pluggable interface means that split won't touch core code.

**Draft `docker-compose.yml`:**
```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: appointment_db
      POSTGRES_USER: app_user
      POSTGRES_PASSWORD: secure_pass
    ports:
      - "5432:5432"
    volumes:
      - pg_data:/var/lib/postgresql/data

  app-engine:
    build: .
    environment:
      SPRING_PROFILES_ACTIVE: erp-mock   # switch to erp-live for production
      DB_HOST: postgres
    ports:
      - "8080:8080"
    depends_on:
      - postgres

volumes:
  pg_data:
```

---

## 8. Example Flow — Reschedule + Payment

1. User reschedules via the web app → `PUT /appointments/{id}/reschedule` with `source: web`.
2. `adapter-rest` converts the request into a domain command.
3. `core-app` loads the `Appointment` aggregate via `adapter-persistence`.
4. `core-domain` applies the rule: `reschedule_count++`, updates `updated_at` and `last_action`.
5. A domain event `AppointmentRescheduled` fires → `adapter-audit` appends a row to `audit_log`.
6. `core-app` calls `EngineConnectorPort.pushAppointment(...)` → `adapter-erp-impl` converts and sends it to the local ERP.
7. `core-app` persists the new state to PostgreSQL. Transaction complete.

---

## 9. Immediate Next Steps (Sprint 0)

1. Scaffold the multi-module Java project (Spring Boot 3.x + Gradle/Maven).
2. Define the `Appointment` / `Payment` domain objects and the `EngineConnectorPort` interface in `core-domain`.
3. Implement `adapter-erp-mock` (`@Profile("erp-mock")`) — logs calls to console only.
4. Set up Flyway migrations for the Postgres schema (`appointments`, `payments`, `audit_log`).
5. Write `docker-compose.yml` to spin up Postgres + the app.
6. Build the first REST endpoint: `POST /appointments` (creates, `reschedule_count = 0`, logs audit, calls mock ERP).

---

## 10. Decisions Deferred

- **Event bus (Kafka/RabbitMQ):** not needed yet — use Spring's in-process `ApplicationEventPublisher` for auditing.
- **Idempotency:** handle at the DB level (unique request-id) only if duplicate-webhook issues actually show up.
- **Scaling the core:** vertical scaling is fine for now; move to an external event bus only if/when we need to scale horizontally.
- **Real ERP integration details** (which fields, SOAP vs REST vs SQL vs flat file): decided once we scope `adapter-erp-impl` against the actual local ERP.

---

*This document is the current source of truth for the architecture direction. Update it as decisions change — don't let it drift from what's actually built.*
