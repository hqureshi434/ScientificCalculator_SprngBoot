# SciCalculator — Claude Code Context

## What this project is

A scientific calculator web app. It is a **learning exercise**, not a product. The
goal is to become fluent in the exact enterprise stack that the next version of the
Legal Chain platform will be built on, and to practice **supervising** an AI coding
agent closely rather than letting it run autonomously.

**Do not optimize for speed or autonomy.** Optimize for the human understanding every
line. Work in small, reviewable steps. After each step, stop and let the human run the
app and read the diff before continuing.

## Repository & environment setup (done 2026-05-29)

- **Repo:** personal GitHub `hqureshi434/ScientificCalculator_SprngBoot`, cloned
  locally. (Hussein said a repo wasn't required for this sandbox; it's kept on a
  personal account purely so the work is retrievable. This is NOT the company repo —
  the company beta repo `LegalChainInc/LegalChainBeta` is separate and untouched.)
- **Project lives in the `SciCalculator/` subfolder** of the clone (the folder that
  contains `build.gradle`, `settings.gradle`, `gradlew`, `gradle/`, and `src/`). If
  Gradle import is flaky, open IntelliJ directly on the `SciCalculator/` folder rather
  than its parent.
- **How the Gradle project was created:** regenerated a fresh Gradle skeleton from
  start.spring.io (Gradle-Groovy, Spring Boot 4.0.6, Java 25, group `com.example`,
  package `com.example.scicalculator`), unzipped it into the clone, copied the existing
  hand-written `src/` and `application.yml` over the stubs, then replaced the generated
  `build.gradle` with the full one (Envers + jjwt + Security + H2 + h2console, none of
  which Initializr provides by default).
- **Migration pitfalls already hit and resolved** (so they don't recur): (a) Gradle
  toolchain needed a matching JDK → foojay plugin `1.0.0` in `settings.gradle`;
  (b) `PathRequest.toH2Console()` was replaced with the literal `/h2-console/**`
  matcher in `SecurityConfig` to avoid a runtime class lookup; (c) the
  `spring-boot-h2console` module had to be added back (the Maven build had it; the
  Gradle regen dropped it) or the console 404s.
- **Verified working:** clean boot with `Envers integration enabled? : true`, all seven
  tables created (`USERS`, `USERS_AUD`, `CALCULATION`, `CALCULATION_AUD`,
  `CALCULATION_STEP`, `CALCULATION_STEP_AUD`, `REVINFO`), and the H2 console loads at
  `http://localhost:8080/h2-console` showing all tables.
- `.gitignore` covers `.gradle/`, `build/`, `.idea/`; the stray `SciCalculator.zip`
  download should be deleted and not committed.

## How to work in this repo (mandatory workflow)

1. Before writing code, state a short plan and wait for approval.
2. Implement **one** small unit at a time (one class, or one cohesive change).
3. Show the full diff. Do not move to the next step until the human says go.
4. After a change that affects startup, the human runs `SciCalculatorApplication` and
   confirms the console before continuing.
5. No long autonomous runs. No multi-task batches. No spawning subagents to build
   ahead. The point is supervised, incremental work.
6. Prefer explicit, readable code over clever abstractions. Explain *why*, not just
   *what*, when introducing a pattern.

## Confirmed stack and versions (do not change without asking)

- **Java 25** (LTS) as the language level locally; toolchain JDK is OpenJDK 26.
  (Hussein runs Java 17 in the real project, but told Hammad not to worry about the
  version for the calculator — 25 local is fine. The real scaffolding will arrive on
  17; match it then, not now.)
- **Spring Boot 4.0.6** (parent BOM manages most dependency versions).
- **Gradle** build (Groovy DSL). **Migration from Maven completed and verified
  2026-05-29** — Hussein directed "We will be using Gradle"; the real Legal Chain
  project is Gradle and his base scaffolding ships as Gradle, so the calculator is on
  Gradle to consume it cleanly. `build.gradle` is the single source of truth; if
  IntelliJ behaves oddly, reload the Gradle project before overriding IDE settings.
  The Gradle toolchain is pinned to Java 25 via the `java { toolchain { ... } }` block,
  and `settings.gradle` uses the **foojay-resolver-convention plugin version `1.0.0`**
  to auto-download a matching JDK (0.8.0 and earlier reference the removed
  `JvmVendorSpec.IBM_SEMERU` and break on Gradle 9 — must be 1.0.0+).
- **Hibernate ORM 7.2.x** via `spring-boot-starter-data-jpa`.
- **Hibernate Envers** (`org.hibernate.orm:hibernate-envers`) for audit trails.
- **Spring Security** with **JWT** via **jjwt 0.13.0**
  (`jjwt-api` compile, `jjwt-impl` + `jjwt-jackson` runtime).
- **H2** in-memory database for the sandbox; console at `/h2-console`,
  JDBC URL `jdbc:h2:mem:scicalc`, user `sa`, no password.
- **`spring-boot-h2console`** — REQUIRED in Spring Boot 4 for the H2 console servlet to
  register. The `com.h2database:h2` driver alone does NOT serve the console; without
  this module `/h2-console` returns 404. Nothing in Java code imports it directly, so
  do NOT remove it as "unused" — it is needed at runtime for the console.
- **Validation** via `spring-boot-starter-validation`.

### Spring Boot 4 naming note
Spring Boot 4 modularized its starters. This project uses `spring-boot-starter-webmvc`
(not `spring-boot-starter-web`) and per-module test starters
(e.g. `spring-boot-starter-webmvc-test`) instead of the single `spring-boot-starter-test`.
**Do not "fix" these to the older Spring Boot 3.x names** — the 4.x names are correct.

### Lombok (now in use)
**Changed 2026-05-29.** Lombok is now used in the real Legal Chain project (Hussein:
"It's a godsend, Lombok... I'm using it."), so the calculator uses it too. Lombok is in
`build.gradle` (`compileOnly` + `annotationProcessor`, plus the test equivalents).
**The entities are still hand-written** — the Gradle migration is done but the Lombok
refactor of the entities has NOT happened yet. `User` is the planned first refactor.

Before relying on Lombok, confirm IntelliJ has **annotation processing enabled**
(Settings → Build, Execution, Deployment → Compiler → Annotation Processors → Enable),
or Lombok-generated getters/setters will show as "cannot find symbol" in the editor
even when Gradle compiles fine.

Discipline that still applies:
- Use the **granular** annotations on entities: `@Getter`, `@Setter`,
  `@NoArgsConstructor` (and `@AllArgsConstructor`/`@Builder` only if actually needed).
- **Never put `@Data` on a JPA entity.** `@Data` pulls in `@ToString` and
  `@EqualsAndHashCode`, both of which break Hibernate identity and can trigger
  lazy-load loops / infinite recursion across relationships.
- Preserve the existing design intent when refactoring: no setter for `id` or
  `createdAt` (use field-level `@Setter(AccessLevel.NONE)` or omit class-level
  `@Setter` and annotate only the mutable fields). Do not silently add setters that
  the hand-written version deliberately left out.
- Add the JPA no-arg constructor via `@NoArgsConstructor`; keep the existing
  business constructor(s).

## Configuration baseline (already set in application.yml)

- Named in-memory H2: `jdbc:h2:mem:scicalc;DB_CLOSE_DELAY=-1`
- `spring.jpa.hibernate.ddl-auto: update` (sandbox only; real Legal Chain will use
  Flyway migrations + `validate`)
- `spring.jpa.open-in-view: false` (deliberate — forces disciplined loading inside the
  transaction)
- `show-sql: true` + `format_sql: true` + `org.hibernate.SQL: DEBUG` so every SQL
  statement, including Envers audit inserts, is visible in the console.

### OPEN ITEM — BigDecimal scale is still wrong
The `BigDecimal` columns (`currentValue`, `operand`, `resultAfter`) were created as
`numeric(38,2)` — precision 38 but **scale 2**, i.e. only two decimal places. The
intended widening to `scale = 10` (so trig/log results aren't silently truncated)
**has not been applied yet.** This must be fixed (`@Column(precision = 38, scale = 10)`
on those three fields) before any real numbers are stored. Because H2 is in-memory and
`ddl-auto: update` won't cleanly shrink/alter an existing column, a restart on a fresh
in-memory DB applies the new type. Verify in the H2 console after the change.

### Also note: a dev-only SecurityConfig already exists
`com.example.scicalculator.configuration.SecurityConfig` was hand-written outside the
planned build order, purely to let the H2 console load past Spring Security
(permits `/h2-console/**`, `frameOptions(sameOrigin)`, CSRF ignored for that path,
`httpBasic`). It returns `http.build()`. This is a **dev convenience only** and will be
**replaced** by the real JWT `SecurityConfig` later. Do not treat it as the final
security config; do not recreate it. When the real JWT config is built, the H2
carve-outs should survive only if scoped to a dev profile.

## Domain model

Package: `com.example.scicalculator.domain`

**Current build state (all of the following are already implemented and verified in
the H2 console — seven tables present, audit tables generated):**

- **`Role`** (enum): `USER`, `ADMIN`. Done.
- **`User`** (`@Entity`, table `users`, `@Audited`): `id`, `username` (unique),
  `passwordHash`, `role` (`@Enumerated(EnumType.STRING)`), `createdAt`
  (`updatable = false`, set via `@PrePersist`). No setters for `id`/`createdAt`.
  Still **hand-written**; the Lombok refactor of this entity is the immediate next
  task (preserve the no-setter-on-id/createdAt intent).
- **`Calculation`** (`@Entity`, `@Audited`): `id`, optional `name`, `owner`
  (ManyToOne -> User, `LAZY`, `optional = false`), `currentValue` (`BigDecimal`,
  seeded to `ZERO`), `createdAt`, `updatedAt` (`@PrePersist` both, `@PreUpdate` for
  `updatedAt`). No setters for `id`/`createdAt`/`owner`. Done.
- **`CalculationStep`** (`@Entity`, `@Audited`): `id`, `calculation` (ManyToOne, `LAZY`,
  `optional = false`), `sequenceNumber` (`int`), `operation`
  (`@Enumerated(EnumType.STRING)`), `operand` (`BigDecimal`), `resultAfter`
  (`BigDecimal`), `createdAt` (`@PrePersist`). Append-only (no setters). DB-level
  `UNIQUE(calculation_id, sequence_number)` named `uk_calculation_step_seq`.
  Unidirectional — `Calculation` has no `List<CalculationStep>`. Done.
- **`Operation`** (enum): flat — `ADD, SUBTRACT, MULTIPLY, DIVIDE, SIN, COS, TAN, LOG,
  LN, POW, SQRT`. Done.
- **`Revision`** — NOT yet built. Custom Envers revision entity extending
  `DefaultRevisionEntity`, adding a `username` column, populated by an
  `EnversRevisionListener` that reads the current user from a thread-bound context.
  See HARD STOP below.

Repositories (`com.example.scicalculator.repository`) — already implemented:
- **`CalculationRepository extends JpaRepository<Calculation, Long>`**: `findByOwner(User)`.
- **`CalculationStepRepository extends JpaRepository<CalculationStep, Long>`**:
  `findByCalculationOrderBySequenceNumberAsc(Calculation)` and
  `findTopByCalculationOrderBySequenceNumberDesc(Calculation)` (returns `Optional`).

JPA notes that must be respected:
- Always use `@Enumerated(EnumType.STRING)` for enums (never ORDINAL).
- Table name `users` (not `user`, which is reserved in many databases).
- Never put `@Data`, `@ToString`, or `@EqualsAndHashCode` on a JPA entity (now that
  Lombok is in use, this matters in practice) — they break Hibernate identity and can
  trigger lazy-load loops. Use granular `@Getter`/`@Setter`/`@NoArgsConstructor`.

## Stack pieces this exercise must demonstrate (Hussein's rubric)

1. **Hibernate Envers** — `@Audited` entities produce `_AUD` tables + `REVINFO`
   automatically. Confirm visually in the H2 console.
2. **`@Transactional`** — applying a calculation step must insert the step AND update
   the parent `Calculation` atomically; a failure (e.g. divide-by-zero after a partial
   write) must roll the whole thing back.
3. **Thread-bound request context** — the authenticated user must reach the Envers
   revision listener via a `ThreadLocal` / `SecurityContextHolder`, not as a method
   parameter. This is the "thread attribute" requirement.
4. **Connection pool** — HikariCP (Spring Boot default) is already running; understand
   its config knobs.
5. **JWT auth** — Spring Security filter chain validates a JWT on every request.

## Planned API surface (small on purpose)

- `POST /api/auth/register` -> `{ token }`
- `POST /api/auth/login` -> `{ token }`
- `POST /api/calculations` -> create empty Calculation
- `POST /api/calculations/{id}/steps` -> append a step, return new currentValue
- `GET  /api/calculations/{id}` -> calculation + steps
- `GET  /api/calculations/{id}/history` -> Envers revision history (proves auditing)

## Mapping back to Legal Chain (why the calculator is shaped this way)

- `Calculation` ≈ `Contract`
- `CalculationStep` ≈ a clause edit / contract revision step
- `Operation` enum ≈ clause change type
- Envers `_AUD` tables ≈ automatic contract version history
- `@Transactional` ≈ atomic multi-part contract updates
- JWT + Spring Security ≈ the auth layer the real backend needs

## Reserved build: Envers revision wiring

Status as of 2026-06-05.

DONE (built via the guided, chat-first process, then handed to Claude Code with
reviewed diffs). These files are complete, test-covered, and no longer off-limits.
Normal edits are fine:
- audit/AuditUserContext.java      hand-written ThreadLocal<String> holder (set/get/clear)
- audit/AuditRevisionEntity.java   @Entity @Table(name="REVINFO")
  @RevisionEntity(AuditRevisionListener.class),
  extends RevisionMapping, adds nullable=false username
- audit/AuditRevisionListener.java RevisionListener; reads AuditUserContext, stamps
  username, falls back to "system" when none is bound

STILL RESERVED (build chat-first with the human, then hand off from a scoped prompt
with diffs to review). Do NOT create or register this autonomously:
- The bridge filter: a OncePerRequestFilter that copies the authenticated username
  from SecurityContextHolder into AuditUserContext at the start of each request and
  calls AuditUserContext.clear() in a finally block, plus its registration in
  SecurityConfig.

Why it stays reserved: it is the one spot where the request thread, the Spring
Security context, the @Transactional write, and Envers all line up at runtime, and
the clear() in finally is what stops a pooled worker thread from leaking one
request's user onto the next.

## Out of scope / do not add

- No Spring Cloud, GraphQL, Docker Compose, GraalVM native, messaging, or NoSQL.
- No `@Data` on entities (Lombok itself is now allowed; `@Data` on entities is not).
- No autonomous multi-hour runs or subagent batch execution for this exercise.
- Do not check this calculator into the company codebase; it is a personal sandbox.

## End-of-session summary (required)

At the end of every working session, before stopping, produce a written summary that
includes:

1. **What was built** — each file created or modified, with a one-line description of
   what it does.
2. **Plan vs. actual** — the plan that was agreed at the start of the session, and
   whether the work followed it.
3. **Deviations** — for anything that differed from the agreed plan (a different
   approach, an extra file, a skipped step, a renamed class, an added dependency,
   etc.), state explicitly what changed **and why**. Do not bury deviations; call each
   one out.
4. **Boundary status** — confirm whether the HARD STOP boundary (revision listener /
   thread-context wiring) was respected and left unimplemented.
5. **Next step** — the single next thing to do, and anything the human should verify
   (e.g. "restart and confirm the `calculation_AUD` table appears in the H2 console").

Keep the summary factual and concise. If there were no deviations, say so explicitly
rather than omitting the section.
