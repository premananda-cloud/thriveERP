# thriveERP — Test Wiring: Architecture, Flow, and Diagnosis

> **Status:** Working doc for one specific problem: getting `mvn test` fully green on Spring Boot 4.1.1.
> **Purpose:** Stop patching blind. Lay out what the test setup *should* look like, what's actually happening at each layer, what's confirmed fixed, and what's still unexplained — so you can diagnose the remaining failure yourself instead of round-tripping error logs.
> **Context:** You're on Spring Boot 4.1.1 / Spring Framework 7 / Spring Security 7 / Jackson 3 — all very new (this doc is being written the same year they shipped). A lot of Boot-3-era knowledge (mine included) is stale for this stack. Several of the bugs found so far are exactly that: correct-for-Boot-3 code that's silently wrong on Boot 4. Treat anything not explicitly confirmed against your actual build output as a hypothesis, not a fact.

---

## 1. What "ideal" looks like — the test pyramid mapped onto your hexagonal layers

Your architecture (ARCHITECTURE.md §2-3) is layered: `core.domain` → `core.app` → adapters. Tests should mirror that — each layer tested with the *minimum* machinery needed, per groundrule.txt §5.

| Layer | What it is | Spring context needed? | DB needed? | Example in this repo |
|---|---|---|---|---|
| `core.domain` | Pure Java, no framework | **No** | No | `UserTest` |
| `core.app` | Orchestration, ports mocked | **No** (plain Mockito) | No | `AuthServiceTest` |
| `adapter.persistence` | JPA mapping | **No** (mock the Spring Data repo interface) | No | `UserPersistenceAdapterTest` |
| `adapter.security` | JWT / hashing | **No** (plain constructor calls) | No | `JwtTokenServiceTest`, `PasswordEncoderAdapterTest` |
| `adapter.rest` (HTTP mapping only) | Controllers, DTOs, exception mapping | **Yes — slice** (`@WebMvcTest`) | No | `AuthControllerTest` |
| `adapter.rest` (security enforcement) | `@PreAuthorize` behavior | **Yes — slice + security filter chain** | No | `UserAdminControllerTest` |
| Whole app | Everything wired together | **Yes — full** (`@SpringBootTest`) | **Yes** (real or in-memory) | `ThriveErpApplicationTests` |

**The key property of a healthy test suite:** only the last row should ever need a real (or fake) database, and it should be exactly one test class, run rarely relative to the others. Everything above it should be fast and infrastructure-free. Right now that property mostly holds — the problem is entirely concentrated in getting Spring's *own* test machinery to boot correctly on this Boot version, not in your code's design.

---

## 2. What actually happens when each test type runs (the call flow)

### 2.1 Unit tests (`UserTest`, `AuthServiceTest`, `UserPersistenceAdapterTest`, `JwtTokenServiceTest`, `PasswordEncoderAdapterTest`)

```
JUnit 5 launcher
  → instantiate test class directly (no Spring involved)
  → for AuthServiceTest / UserPersistenceAdapterTest: Mockito creates mock
    implementations of the port/repository interfaces
  → test calls the real class under test with mocks/real objects
```

Nothing here talks to Spring's `ApplicationContext`. These are confirmed passing (per your earlier surefire output — 0 failures on these classes). **Not part of the current problem.**

### 2.2 `@WebMvcTest` slice tests (`AuthControllerTest`, `UserAdminControllerTest`)

```
@WebMvcTest(controllers = X.class)
  → Spring Boot's test slice machinery builds a MINIMAL ApplicationContext:
      - the named controller(s)
      - @ControllerAdvice classes you @Import
      - a curated subset of autoconfiguration (WebMvcAutoConfiguration,
        JacksonAutoConfiguration, ValidationAutoConfiguration, ErrorMvcAutoConfiguration...)
      - ANY bean in your codebase whose TYPE matches one of the slice's
        default include-filters — this is the part that bit us. Boot's
        default include filters for @WebMvcTest cover: @Controller,
        @ControllerAdvice, @JsonComponent, Converter/GenericConverter,
        HttpMessageConverter, WebMvcConfigurer, HandlerMethodArgumentResolver,
        and — critically — javax/jakarta.servlet.Filter. Your
        JwtAuthenticationFilter is a @Component that IS a Filter, so it gets
        swept into EVERY @WebMvcTest context automatically, whether or not
        you @Import SecurityConfig, and whether or not @AutoConfigureMockMvc
        (addFilters = false) is set (that flag controls whether MockMvc
        *applies* filters, not whether Spring *constructs* the bean).
  → context refresh instantiates all those beans
  → MockMvc is built against that context
  → test sends a fake HTTP request through MockMvc, assertions run against
    the response
```

**Two confirmed bugs found here, both fixed:**
- The auto-swept `JwtAuthenticationFilter` needs a `JwtTokenService` bean to construct. Nothing provided one → `NoSuchBeanDefinitionException`. Fixed by adding `TestJwtTokenServiceConfig` (a `@TestConfiguration` supplying just that bean) and importing it.
- `UserAdminControllerTest` additionally declared its *own* `@Bean JwtAuthenticationFilter` — which collided by bean name with the auto-swept real one → `BeanDefinitionOverrideException`. Fixed by deleting that bean method and letting the auto-swept one construct on its own.
- `@Autowired ObjectMapper objectMapper` in both test classes was importing the **Jackson 2** type (`com.fasterxml.jackson.databind.ObjectMapper`). Spring Boot 4 auto-configures a **Jackson 3** `JsonMapper` (`tools.jackson.databind.json.JsonMapper`), which is a different, unrelated class hierarchy — Jackson 3 renamed almost its entire package namespace from `com.fasterxml.jackson` to `tools.jackson`. No bean of the Jackson-2 type existed → `NoSuchBeanDefinitionException`. Fixed by importing `tools.jackson.databind.ObjectMapper` instead (the method `writeValueAsString(...)` is unchanged, only the package moved).

**Status:** these two test classes *should* now build their contexts successfully. If they still fail after the last fix, the error will look different from anything above — read the new stack trace fresh rather than assuming it's a repeat.

### 2.3 `@SpringBootTest` full-context test (`ThriveErpApplicationTests.contextLoads`)

```
@SpringBootTest(classes = ThriveErpApplication.class)  (implicit, via @SpringBootApplication scan)
  → boots the ENTIRE application context:
      - every @Component/@Service/@Repository under com.thriveerp.thriveERP
      - full autoconfiguration (not a curated subset like @WebMvcTest)
      - real DataSource bean → attempts an actual JDBC connection
      - Flyway bean → runs migrations against that DataSource at startup
      - JwtTokenService → constructor runs eagerly, throws if
        app.jwt.secret is blank or <32 bytes
  → if ANY bean fails to construct, or Flyway migration fails, or the
    DataSource can't connect, the whole context fails to load and
    contextLoads() reports "Failed to load ApplicationContext"
```

This is the one that's still failing, and — this matters — **we don't have its actual root cause**. Every stack trace pasted so far has been cut off before this test's own `Caused by:` line. Everything below is informed hypothesis, ranked by likelihood, not a confirmed diagnosis.

---

## 3. `ThriveErpApplicationTests` — ranked hypotheses

### H1 (most likely): `src/test/resources/application.yaml` isn't actually overriding `src/main/resources/application.yaml`

**Why it might happen:** Spring Boot's config loader resolves `classpath:/application.yaml` by asking the classloader for that resource. Maven puts `target/test-classes` before `target/classes` on the test classpath, so in a normal setup the test version wins outright. But if your IDE or a Maven plugin is building differently (e.g. `test-classes` not appearing first, or the file didn't actually get copied by `resources:testResources` — check the build log for a line like `Copying 1 resource from src/test/resources to target/test-classes`), the *main* `application.yaml` could still be in effect. That version has `secret: ${JWT_SECRET:}` (blank default) and a real Postgres URL — both would independently fail context load.

**How to check:** In your last full `mvn test` output, look for the `resources:testResources` step and confirm it copied `application.yaml`. Also just try: `unzip -p target/test-classes/../test-classes/application.yaml` (or `cat target/test-classes/application.yaml`) after a build and confirm it's the H2 version, not the Postgres one.

**If confirmed:** rename it to `application-test.yaml` and add `@ActiveProfiles("test")` to `ThriveErpApplicationTests` instead of relying on filename-based override — more explicit, less magic.

### H2: H2's PostgreSQL compatibility mode doesn't actually accept `V1__create_users_table.sql` as-is

**Why it might happen:** `MODE=PostgreSQL` is a best-effort compatibility layer, not a full Postgres reimplementation. Two specific things in that migration are worth checking: the `UUID PRIMARY KEY` column type, and `DEFAULT now()`. Both are usually fine in H2's PostgreSQL mode, but "usually" isn't "confirmed on your exact H2 version."

**How to check:** the error, if this is the cause, would show up as a Flyway `FlywayException` wrapping a SQL syntax error, with the specific offending statement quoted. Search the (currently missing) stack trace for `org.flywaydb.core.api.FlywayException` or `org.h2.jdbc.JdbcSQLSyntaxErrorException`.

**If confirmed:** simplest fix is dropping `ddl-auto: validate` to `ddl-auto: create-drop` for the test profile only and disabling Flyway there (`spring.flyway.enabled: false` in test config) — let Hibernate generate the schema from the entities instead of running the Postgres-flavored migration against H2. This is a common, accepted pattern specifically because Flyway migrations are often written for the real target DB and aren't meant to double as the test-DB schema source.

### H3: Hibernate's `ddl-auto: validate` rejects the H2-created schema as not matching `UserJpaEntity`

**Why it might happen:** `validate` mode is strict about column types. Hibernate might expect a `uuid` SQL type for the `id`/`appointment_id`-style UUID columns and H2 might report something Hibernate doesn't consider an exact match, even though the data round-trips fine.

**How to check:** error would be `SchemaManagementException: Schema-validation: wrong column type` with the specific column named.

**If confirmed:** same fix as H2 — use `create-drop` for tests instead of `validate` against a migration-authored schema.

### H4 (less likely but cheap to rule out): a second, unrelated Jackson-3 bean mismatch inside full autoconfiguration

**Why it might happen:** the `@WebMvcTest` fix was about a *test class field* using the wrong Jackson type. In the full context, nothing in your own code currently `@Autowired`s an `ObjectMapper`/`JsonMapper` directly (checked: `AuthController`, `UserAdminController`, etc. don't inject one), so this is unlikely to be the cause here — but worth a 10-second grep if H1–H3 don't pan out.

**How to check:** `grep -rn "ObjectMapper\|JsonMapper" src/main` — if nothing shows up, rule this out.

### H5 (unlikely): datasource connection actually attempted against real Postgres despite H2 config

Symptom would be `PSQLException: Connection refused` or similar. This would actually just be a more severe case of H1 (the override didn't take at all). Same fix.

---

## 4. What to send back to keep this moving

The fastest path to an actual fix, instead of another guess-and-patch cycle: run `mvn test`, then open `target/surefire-reports/com.thriveerp.thriveERP.ThriveErpApplicationTests.txt` directly (not the console output — the `.txt` report has the full, untruncated stack trace) and paste **from the first `Caused by:` onward**. That one block of text will most likely point straight at H1, H2, or H3 above rather than needing more hypothesis-generation.

If you'd rather just get to green fast without the diagnostic step: apply the H1+H2/H3 fixes preemptively (they're cheap, low-risk, and correct regardless of which one is the actual cause) —
1. Confirm `src/test/resources/application.yaml` is being picked up (or switch to an explicit `@ActiveProfiles("test")` + `application-test.yaml` to remove the ambiguity entirely).
2. In the test config, set `spring.jpa.hibernate.ddl-auto: create-drop` and `spring.flyway.enabled: false` — let Hibernate own the schema for this one smoke test, and keep Flyway strictly for real Postgres deployments per ARCHITECTURE.md §7.

---

## 5. Reference: Spring Boot 4 breaking changes hit so far in this project

Keeping this list here so the next surprise doesn't cost another round trip. Update it as more turn up — this repo is an early Boot 4 adopter, so more are plausible.

| Area | Boot 3 behavior | Boot 4 behavior | Where it bit us |
|---|---|---|---|
| `@WebMvcTest` / `@AutoConfigureMockMvc` | In `spring-boot-test-autoconfigure`, package `org.springframework.boot.test.autoconfigure.web.servlet` | Moved to new `spring-boot-starter-webmvc-test` artifact, package `org.springframework.boot.webmvc.test.autoconfigure` | Compile error, first `mvn test` attempt |
| Mocking beans in tests | `@MockBean` (`org.springframework.boot.test.mock.mockito`) | Removed entirely. Use `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`, Spring Framework 7) | Same compile error batch |
| JSON mapper auto-configuration | `ObjectMapper` (`com.fasterxml.jackson.databind`) is the auto-configured bean type | `JsonMapper` (`tools.jackson.databind.json`) is auto-configured; entire Jackson package namespace renamed `com.fasterxml.jackson` → `tools.jackson` (annotations excepted) | `AuthControllerTest`/`UserAdminControllerTest` `NoSuchBeanDefinitionException` |
| `@WebMvcTest` component scan | Same general behavior | Still auto-includes `Filter`-type beans by default — not new to Boot 4, but easy to forget when a hand-rolled `JwtAuthenticationFilter` exists | `JwtAuthenticationFilter` construction failure in slice tests |
| Autoconfiguration package layout | Mostly under `org.springframework.boot.autoconfigure.*` | Split per-module: `org.springframework.boot.webmvc.autoconfigure`, `org.springframework.boot.jackson.autoconfigure`, `org.springframework.boot.data.autoconfigure.web`, etc. | Visible in context-customizer dumps in your stack traces; informational, not itself a bug |
| Flyway + Postgres | `flyway-core` alone sufficient | Postgres support split into `flyway-database-postgresql` (added to `pom.xml` already); H2 support remains bundled in `flyway-core` | Already handled in current `pom.xml` |

---

*This doc is diagnostic, not a permanent architecture reference — once `mvn test` is green, fold anything durable (e.g. the "tests never need a live DB except one smoke test" rule, or the Boot 4 gotchas table) into CODE_STRUCTURE.md or groundrule.txt and this file can be archived or deleted.*
