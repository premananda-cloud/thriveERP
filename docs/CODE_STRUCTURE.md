# thriveERP — Code Structure

> **Status:** Living document — append to this as new code lands, don't rewrite history.
> **Purpose:** What actually exists in `src/`, package by package, and why it's organized that way — so the next person (or model) can design the next piece without reading the code first.
> **Relationship to other docs:** ARCHITECTURE.md says where things *should* live (the plan). This doc says where things *actually* live right now (the reality). DesignLogic.md says what the business rules are. This doc doesn't repeat business rules — it points at the file that has them.
>
> **How to keep this versioned:** every time a slice of functionality lands, add a row to §1 (Change Log) and a section under §3 (Modules). Don't delete old entries when something changes — add a new dated entry and mark the old one superseded. Treat this file like a ledger, not a snapshot.

---

## 1. Change Log

| Date | Change | Detail |
|---|---|---|
| 2026-09-10 | Auth slice added | User registration, login (JWT), role management. First real feature built on top of the Sprint-0 skeleton. See §3.1. |
| 2026-09-09 | Project scaffolded | Single Spring Boot module (`com.thriveerp.thriveERP`), no business logic yet. Matches ARCHITECTURE.md §9 Sprint 0 item 1. |

---

## 2. Current Physical Layout

Still **one Spring Boot module** (multi-module Maven split is deferred — see ARCHITECTURE.md §9 item 1 / tech_version.md). Inside that one module, packages are laid out to mirror the hexagonal layers from ARCHITECTURE.md §2–3, so that splitting into real Maven modules later is a move, not a rewrite.

```
src/main/java/com/thriveerp/thriveERP/
├── ThriveErpApplication.java        entry point (@SpringBootApplication)
│
├── core/
│   ├── domain/                      pure Java. no Spring, no JPA, no Jackson. (groundrule.txt §2)
│   │   └── user/
│   │       ├── User.java
│   │       ├── Role.java
│   │       ├── UserRepositoryPort.java
│   │       ├── PasswordEncoderPort.java
│   │       ├── TokenServicePort.java
│   │       └── exception/
│   │           ├── DuplicateUserException.java
│   │           ├── InvalidCredentialsException.java
│   │           └── UserNotFoundException.java
│   │
│   └── app/                         orchestration (use cases). talks to ports only.
│       └── user/
│           └── AuthService.java
│
└── adapter/
    ├── persistence/                 implements the *Port interfaces from core.domain, using JPA.
    │   └── user/
    │       ├── UserJpaEntity.java
    │       ├── UserJpaRepository.java
    │       └── UserPersistenceAdapter.java
    │
    ├── security/                    implements PasswordEncoderPort / TokenServicePort,
    │   │                            plus Spring Security wiring (stateless JWT).
    │   ├── PasswordEncoderAdapter.java
    │   ├── JwtTokenService.java
    │   ├── JwtAuthenticationFilter.java
    │   └── SecurityConfig.java
    │
    └── rest/                        HTTP in/out. DTOs + controllers. no business rules live here.
        ├── auth/
        │   ├── AuthController.java
        │   └── dto/
        │       ├── RegisterRequest.java
        │       ├── LoginRequest.java
        │       ├── AuthResponse.java
        │       └── UserResponse.java
        ├── user/
        │   ├── UserAdminController.java
        │   └── dto/
        │       └── ChangeRoleRequest.java
        └── common/
            └── GlobalExceptionHandler.java

src/main/resources/
├── application.yaml                 config, sourced from env vars — see §4
└── db/migration/
    └── V1__create_users_table.sql   Flyway migration
```

**Rule for adding new features:** every new feature gets the same four-layer shape — `core/domain/<feature>`, `core/app/<feature>`, `adapter/persistence/<feature>` (if it needs storage), `adapter/rest/<feature>` (if it's exposed over HTTP). Don't put a new feature directly under `adapter/rest` without a `core/domain` counterpart, even if it feels trivial — that's the shortcut that erodes the hexagonal boundary over time (groundrule.txt §2).

---

## 3. Modules

### 3.1 Auth (`user` feature) — added 2026-09-10

**What it does:** registration, login (issues a JWT), and role grant (admin-only). Not in ARCHITECTURE.md's original data model (`appointments`/`payments`/`audit_log`) — this is new scope layered on using the same pattern.

| Class | Layer | Responsibility |
|---|---|---|
| `User` | core.domain | The user aggregate. Fields: id, username, email, passwordHash, role, createdAt, updatedAt. `newRegistration()` factory defaults role to `CUSTOMER`. `changeRole()` is the only way to escalate. |
| `Role` | core.domain | Enum: `CUSTOMER`, `STAFF`, `ADMIN`. |
| `UserRepositoryPort` | core.domain | Storage contract — save/find/exists. No implementation detail leaks through it. |
| `PasswordEncoderPort` | core.domain | hash/matches contract. Hides which hashing algorithm is used. |
| `TokenServicePort` | core.domain | issueToken contract. Hides which token format (JWT) is used. |
| `DuplicateUserException` / `InvalidCredentialsException` / `UserNotFoundException` | core.domain | Domain-level failure signals. Mapped to HTTP status only in `GlobalExceptionHandler`. |
| `AuthService` | core.app | `register()`, `login()`, `changeRole()`. Orchestrates the ports above. Zero knowledge of HTTP, JWT libraries, or SQL. |
| `UserJpaEntity` | adapter.persistence | JPA mapping for the `users` table. Package-private outside this package on purpose — `core.app` never sees it. |
| `UserJpaRepository` | adapter.persistence | Spring Data repository, package-private. |
| `UserPersistenceAdapter` | adapter.persistence | Implements `UserRepositoryPort`. **Load-then-update** on save (not a fresh-entity blind save) — required because IDs are app-assigned UUIDs, not DB-generated; see inline comment for why the naive version breaks optimistic locking. |
| `PasswordEncoderAdapter` | adapter.security | Implements `PasswordEncoderPort` with BCrypt. |
| `JwtTokenService` | adapter.security | Implements `TokenServicePort`. Issues + parses JWTs (jjwt 0.13.0, HS256). Fails fast at startup if `JWT_SECRET` is missing or under 32 bytes. |
| `JwtAuthenticationFilter` | adapter.security | Reads `Authorization: Bearer <token>`, populates `SecurityContext` if valid. Silently no-ops if missing/invalid — downstream Spring Security handles the 401/403. |
| `SecurityConfig` | adapter.security | Stateless session policy, CSRF off, `/api/auth/register` + `/api/auth/login` public, everything else authenticated. `@EnableMethodSecurity` turns on `@PreAuthorize`. |
| `AuthController` | adapter.rest | `POST /api/auth/register`, `POST /api/auth/login`. Pure translation — no rules. |
| `UserAdminController` | adapter.rest | `PUT /api/users/{id}/role`, `@PreAuthorize("hasRole('ADMIN')")`. Deliberately a separate controller from `AuthController` so "public" vs "admin-only" is visible at a glance. |
| `GlobalExceptionHandler` | adapter.rest | Maps domain exceptions → HTTP status (409/401/404/400). The one place allowed to know both "domain exception" and "HTTP status" at once. |
| `V1__create_users_table.sql` | resources | Flyway migration for `users`. No seed admin row — see note in the file for how to bootstrap the first `ADMIN`. |

**Known open items (not yet solved, tracked here instead of forgotten):**
- No seed/bootstrap path for the first `ADMIN` user beyond a manual `UPDATE` statement.
- No password reset / email verification flow.
- No refresh-token or logout/blacklist mechanism — JWTs just expire (`JWT_EXPIRATION_SECONDS`).
- No rate limiting on `/api/auth/login`.

---

## 4. Configuration Surface

| Env var | Used by | Notes |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | `application.yaml` → datasource | Matches `docker-compose.yml` service names in ARCHITECTURE.md §7. |
| `JWT_SECRET` | `JwtTokenService` | Required, ≥32 bytes. No default — app refuses to start without it. |
| `JWT_EXPIRATION_SECONDS` | `JwtTokenService` | Defaults to 3600 if unset. |

See `.env.example` at repo root for the full list with generation instructions.

---

## 5. Template for New Entries

When a new feature/module lands, copy this block into §3 and fill it in — keeps every entry the same shape so this file stays skimmable.

```md
### 3.N <Feature name> — added <YYYY-MM-DD>

**What it does:** <one or two sentences>

| Class | Layer | Responsibility |
|---|---|---|
| ... | core.domain | ... |
| ... | core.app | ... |
| ... | adapter.persistence | ... |
| ... | adapter.rest | ... |

**Known open items:**
- ...
```

---

*This file describes what's built, not what's planned — planned/deferred items belong in DesignLogic.md §6 or ARCHITECTURE.md §10. If this file and the actual code disagree, the code wins; fix this file.*
