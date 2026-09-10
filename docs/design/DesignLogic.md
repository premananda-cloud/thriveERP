# thriveERP — Design Logic

> **Status:** Draft — expand as rules get confirmed.
> **Purpose:** The actual business rules living inside `core-domain`. ARCHITECTURE.md says *where* logic lives; this doc says *what* the logic is.

---

## 1. Appointment Lifecycle

### 1.1 Status state machine

```
pending ──confirm──▶ confirmed ──start──▶ in_progress ──complete──▶ completed
   │                     │
   └──cancel──▶ canceled │
                         └──cancel──▶ canceled
   (no_show is set instead of completed if the customer never shows)
```

Rules:
- A status change is only valid along the arrows above — no jumping from `completed` back to `pending`, no `canceled` → anything.
- Every transition is written by `core-domain`, never by an adapter. Adapters only carry the *request* to change status; the domain decides if it's legal.
- Every transition fires a domain event (`AppointmentStatusChanged`) for the audit adapter to pick up.

*(Open question: does `in_progress` matter for your business, or is `confirmed → completed` enough? Trim the state machine to match reality — don't model a step nobody uses.)*

### 1.2 Rescheduling

- A reschedule = a change to `scheduled_at` on an appointment that is not yet `completed`, `canceled`, or `no_show`.
- Each reschedule: `reschedule_count++`, `updated_at = now()`, `last_action = <source>`.
- *(Open question: is there a max reschedule count before the system blocks further changes and requires staff intervention? If yes, define it here.)*

### 1.3 Cancellation

- Allowed from `pending` or `confirmed`.
- *(Open question: cancellation policy — free cancel window vs. cutoff time vs. cancellation fee. This affects the Payment logic below and should be decided before coding the cancel endpoint.)*

---

## 2. Payment Logic

- A `payment` record is tied 1:1 (or 1:many, if partial payments/deposits are allowed — decide this) to an `appointment`.
- `payment.status`: `pending → paid`, or `pending → failed`, or `paid → refunded`.
- *(Open question: deposit vs. full payment at booking? Pay at time of service? This changes whether `payments` needs a `type` field: `deposit` / `balance` / `full`.)*
- Refund logic should live in `core-domain` as a rule (e.g. "full refund if canceled >24h before `scheduled_at`, else no refund") — not hardcoded in the payment adapter.

---

## 3. Audit / History Rules

- Anything that changes `appointments` or `payments` state must emit a domain event.
- The audit adapter is dumb by design: it just persists `{action, old_value, new_value, actor, source, timestamp}`. No business logic in the audit adapter — if audit needs to *decide* something, that decision belongs in `core-domain` instead.

---

## 4. ERP / Data Backend (iDempiere)

- **iDempiere is the data backend**, not just a downstream system we notify. The plan is to run iDempiere itself (trimmed) and have thriveERP's appointment engine sit in front of / alongside it, reading and writing through `adapter-erp-impl`.
- Source repo is kept as a **local zip snapshot** (`idempiere/repository_zips/idempiere-2026-Spt-8.zip`) rather than a live clone, since the full iDempiere repo is large and slow to pull. Treat the zip as the pinned baseline version we build from — see `tech_version.md` for how it's tracked.
- `adapter-erp-impl` still implements `EngineConnectorPort`, but its job is broader than "push an invoice": it's the boundary between our domain model and however iDempiere's schema/services actually work. Integration method (REST/SOAP webservices vs. direct DB access against iDempiere's Postgres schema) is still **TBD** and should get decided once the trimmed build is running and we can see what it exposes.
- *(Open question: does iDempiere own the "service" catalog / pricing, or does thriveERP? Since iDempiere is now the data backend rather than a satellite system, the default assumption should probably flip — iDempiere as source of truth, thriveERP as the appointment-specific layer on top. Confirm before building `adapter-erp-impl`.)*

### 4.1 Production packaging plan (draft)

- Build a **trimmed iDempiere distribution** — strip it down to the modules/plugins actually needed for this use case, rather than shipping the full stock build.
- Package the trimmed iDempiere build **together with the thriveERP application** into a single Docker image/compose stack for a given deployment, rather than treating them as two independently-versioned products.
- **Per-enterprise customization via branches**: a base branch holds the generic trimmed-iDempiere + thriveERP template; each client/enterprise gets its own branch off that base for their specific customizations (data model tweaks, plugin selection, branding, etc). Common fixes get merged back from a client branch to base when they're not client-specific.
- *(Open question: how much diverges per client — just config, or actual code/schema changes? That determines whether branch-per-client is sustainable long-term or whether we eventually need a plugin/config-driven customization model instead of branching. Worth revisiting once 2–3 client branches exist.)*

---

## 5. Validation Rules (business, not just data-shape)

- No double-booking: two `confirmed` appointments can't overlap for the same resource/user_id — *(define what "resource" means for you: a person, a room, a service slot?)*
- *(Open question: are there business hours / blackout dates that limit valid `scheduled_at` values?)*

---

## 6. Things Deliberately Not Decided Yet

Keep this section honest — it's more useful than pretending we've solved everything:
- Max reschedule count / lockout behavior
- Cancellation & refund policy specifics
- Partial payment / deposit support
- What "resource" means for double-booking checks
- iDempiere integration method (webservice vs. DB-level)
- Business hours / availability rules
- Source of truth for service catalog/pricing (iDempiere vs. thriveERP)
- What's allowed to diverge per client branch (config-only vs. code/schema)

---

*Update this file as each open question gets an answer — that's more valuable than getting it "complete" up front.*
