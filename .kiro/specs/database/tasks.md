# Tasks — Database

Database side only: schema, migrations, seed data and their tests. The schema
is in `design.md` §4. Each task ends green (`sh gradlew test`). Migrations are
append-only once applied: never edit V1/V2, add a new `V{n}__*.sql` instead.

**Out of scope for this spec:** Java backend code (entities, repositories,
services, REST APIs, grading), admin persistence, media storage, filtering
and any frontend changes. Those are planned separately, step by step, only
when requested.

---

- [x] 1. Infrastructure
  - Added `spring-boot-starter-flyway`, `flyway-database-postgresql`,
    `org.postgresql:postgresql`. Removed H2.
  - Dev database: the existing local `postgres:16` container on port 5433, new
    database `bandup` (separate from other projects on that server). No
    `compose.yaml` needed. `application.yaml` reads `BANDUP_DB_URL`,
    `BANDUP_DB_USERNAME`, `BANDUP_DB_PASSWORD` (local defaults), with
    `ddl-auto=validate` and `open-in-view=false`.
  - Tests: `spring-boot-testcontainers` + `testcontainers-postgresql` 2.x;
    `TestcontainersConfiguration` (`@ServiceConnection`, `postgres:16`) imported
    by every `@SpringBootTest`, so tests never touch the dev database.
  - _Verified_: all 10 tests green against Testcontainers (incl. "runs on
    PostgreSQL 16" and "no pending/failed migrations"); `bootRun` connects to
    `localhost:5433/bandup` and Flyway creates its history table.
  - _Requirements: NFR 1, NFR 2_

- [x] 2. `V1__core_schema.sql`
  - All 26 tables, constraints and indexes from design §4, plus fixes found
    while writing it: a question's group must belong to the same exercise
    (composite FK), stage position only together with a stage, lower-case email
    check, indexes for the evidence/attempt FK checks, and explicit names for the
    key constraints (for clear error messages later).
  - _Verified_: `SchemaConstraintsTest` (22 tests, `@JdbcTest` + Testcontainers)
    covers the threshold/timer rules, option owners and duplicate keys,
    same-exercise group and evidence, blocked evidence/exercise deletes, cascade
    delete without attempts, points and score limits, computed `is_correct`,
    duplicate translations, unknown language, upper-case email and zero word
    limit; 12 of them assert the exact constraint name. All 32 tests green.
    Applied to dev `bandup`: Flyway v1 success, 26 tables.
  - _Requirements: 1.5, 2, 3.2, 6_

- [x] 3. `V2__seed_reference_and_demo.sql`
  - Languages (`en`, `az` enabled; `tr`, `ru`, `uz`, `kk`→`ru`, `ar` rtl
    prepared but disabled), the 13 question types, 5 topics (en/az names), the
    IELTS Reading course with Stage 1 (en/az), and `yellowstone-wolves`
    (PUBLISHED, MINI, B1, nature, Stage 1 position 4).
  - The Yellowstone part was generated from `reading/fixtures.js`: 25 glosses
    converted from `data-az` to `data-g` keys with `az` meanings, 5 evidence
    spans, 6 questions with answers and `en` explanations, FILL `max_words = 1`.
  - _Verified_: `SeedDataTest` (8 tests): only en/az enabled, every fallback
    chain ends at `en`, question types match `admin/questionTypes.js` family by
    family (+ the 2 new ones), the 25 glosses equal the fixture's
    (word, meaning) pairs in order, HTML keys and gloss/evidence rows agree, the
    questions in derived order have the right type, evidence, answers and an
    explanation. `SchemaConstraintsTest` now relies on the seeded reference
    data. All 40 tests green; dev `bandup` at Flyway v2.
  - _Requirements: 1.2, 1.3_

- [ ] 4. (Proposed, not started) `V3__english_gloss_definitions.sql`
  - The Wolves glosses only have `az` meanings. Every fallback chain ends at
    `en`, so without English definitions a learner in any other language sees
    no word help. Add simple English definitions for the 25 glosses
    (`language_code = 'en'`).
  - Needs the definitions written/approved by you before it's a migration.
  - _Verify_: seed test that every Wolves gloss has an `en` meaning.
  - _Requirements: 1.4_
