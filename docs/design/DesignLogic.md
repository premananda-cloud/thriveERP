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

## 4. ERP Sync Rules (iDempiere)

- We're targeting **iDempiere** as the local ERP (see `/idempiere/repository_zips`).
- `adapter-erp-impl` is responsible for translating a confirmed appointment + payment into whatever iDempiere expects (likely a Sales Order / Invoice via iDempiere's REST or SOAP webservices, or direct DB writes if going the DB-adapter route — **to be decided** once we've looked at what iDempiere exposes).
- Sync direction: **push only** for now (thriveERP → iDempiere) unless we find we need availability/resource data pulled *from* iDempiere.
- *(Open question: does iDempiere own the "service" catalog / pricing, or does thriveERP? This decides which system is the source of truth and needs to be settled before building `adapter-erp-impl`.)*

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

---

*Update this file as each open question gets an answer — that's more valuable than getting it "complete" up front.*
