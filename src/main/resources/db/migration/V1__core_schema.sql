-- =============================================================================
-- V1 — BandUp core schema (26 tables).
-- Source of truth: .kiro/specs/database/design.md §4. Never edit this file once
-- merged; schema changes go in a new V{n}__*.sql migration.
--
-- Language rule: exam content (target language) lives on the main row; help
-- text (support languages) lives in *_translation tables keyed
-- (owner_id, language_code).
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Reference
-- -----------------------------------------------------------------------------

create table language (
    code          varchar(16) primary key,                -- BCP-47: 'en', 'az', 'pt-BR'
    name_en       text        not null,                   -- 'Azerbaijani'
    name_native   text        not null,                   -- 'Azərbaycanca'
    direction     varchar(3)  not null default 'ltr' check (direction in ('ltr', 'rtl')),
    fallback_code varchar(16) references language (code), -- next language to try when a translation is missing
    is_target     boolean     not null default false,     -- can be learned/tested
    is_support    boolean     not null default true,      -- can be a learner's help language
    enabled       boolean     not null default false,     -- offered in language pickers
    sort_order    int         not null default 0
);

create table question_type (                              -- mirrors static/js/admin/questionTypes.js
    code    varchar(32) primary key,                      -- 'TFNG', 'FILL', 'MATCH_HEADINGS', ...
    family  varchar(16) not null check (family in ('choiceFixed', 'gap', 'mcSingle', 'mcMulti', 'match')),
    enabled boolean     not null default true
);


-- -----------------------------------------------------------------------------
-- Users (credentials/identities come with the auth spec)
-- -----------------------------------------------------------------------------

create table app_user (
    id               bigint generated always as identity primary key,
    public_id        uuid        not null unique default gen_random_uuid(),
    email            text        unique constraint app_user_email_lowercase_check check (email = lower(email)),
    display_name     text,
    ui_language      varchar(16) not null references language (code),
    support_language varchar(16) not null references language (code),
    target_language  varchar(16) not null default 'en' references language (code),
    status           varchar(16) not null default 'ACTIVE' check (status in ('ACTIVE', 'DISABLED', 'DELETED')),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

create table user_role (
    user_id bigint      not null references app_user (id) on delete cascade,
    role    varchar(16) not null check (role in ('LEARNER', 'AUTHOR', 'TRANSLATOR', 'ADMIN')),
    primary key (user_id, role)
);


-- -----------------------------------------------------------------------------
-- Curriculum (optional grouping; never locks anything)
-- -----------------------------------------------------------------------------

create table course (
    id              bigint generated always as identity primary key,
    slug            varchar(80) not null unique,
    skill           varchar(16) not null check (skill in ('READING', 'LISTENING', 'WRITING', 'SPEAKING', 'GRAMMAR')),
    target_language varchar(16) not null references language (code),
    position        int         not null,
    status          varchar(16) not null default 'DRAFT' check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

create table course_translation (
    course_id     bigint      not null references course (id) on delete cascade,
    language_code varchar(16) not null references language (code),
    title         text        not null,
    description   text,
    primary key (course_id, language_code)
);

create table stage (
    id        bigint generated always as identity primary key,
    course_id bigint not null references course (id),
    position  int    not null,
    unique (course_id, position)
);

create table stage_translation (
    stage_id      bigint      not null references stage (id) on delete cascade,
    language_code varchar(16) not null references language (code),
    title         text        not null,                   -- 'Stage 1' / 'Mərhələ 1'
    primary key (stage_id, language_code)
);


-- -----------------------------------------------------------------------------
-- Exercise (all skills), topics, media
-- -----------------------------------------------------------------------------

create table exercise (
    id                         bigint generated always as identity primary key,
    slug                       varchar(120) not null unique,       -- public id: /reading/{slug}
    skill                      varchar(16)  not null check (skill in ('READING', 'LISTENING', 'WRITING', 'SPEAKING', 'GRAMMAR')),
    target_language            varchar(16)  not null references language (code),
    stage_id                   bigint       references stage (id), -- optional grouping
    position                   int,                                -- order within the stage
    title                      text         not null,              -- target language
    format                     varchar(8)   not null default 'MINI' check (format in ('MINI', 'FULL')),
    level                      varchar(2)   check (level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    duration_seconds           int,                                -- null = no timer
    low_time_threshold_seconds int,                                -- null = no low-time warning
    status                     varchar(16)  not null default 'DRAFT' check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    revision                   int          not null default 1 check (revision >= 1), -- attempts record which one they answered
    version                    bigint       not null default 0,    -- JPA @Version (optimistic lock)
    published_at               timestamptz,
    created_at                 timestamptz  not null default now(),
    updated_at                 timestamptz  not null default now(),
    created_by                 bigint       references app_user (id),
    updated_by                 bigint       references app_user (id),
    constraint exercise_duration_check
        check (duration_seconds is null or duration_seconds > 0),
    constraint exercise_low_time_check
        check (low_time_threshold_seconds is null
               or (duration_seconds is not null
                   and low_time_threshold_seconds > 0
                   and low_time_threshold_seconds <= duration_seconds)),
    constraint exercise_stage_position_check                       -- in a stage => has a position
        check ((stage_id is null) = (position is null)),
    unique (stage_id, position)
);
create index exercise_published_idx on exercise (stage_id, position) where status = 'PUBLISHED';
create index exercise_browse_idx    on exercise (skill, format, level) where status = 'PUBLISHED';

create table topic (
    id       bigint generated always as identity primary key,
    slug     varchar(60) not null unique,                 -- 'nature' (used in filter URLs)
    position int         not null default 0
);

create table topic_translation (
    topic_id      bigint      not null references topic (id) on delete cascade,
    language_code varchar(16) not null references language (code),
    name          text        not null,                   -- 'Nature' / 'Təbiət'
    primary key (topic_id, language_code)
);

create table exercise_topic (
    exercise_id bigint not null references exercise (id) on delete cascade,
    topic_id    bigint not null references topic (id) on delete cascade,
    primary key (exercise_id, topic_id)
);
create index exercise_topic_topic_idx on exercise_topic (topic_id);

create table media (                                      -- metadata only; files live in file storage
    id          bigint generated always as identity primary key,
    public_id   uuid        not null unique default gen_random_uuid(),
    kind        varchar(8)  not null check (kind in ('IMAGE', 'AUDIO')),
    storage_key text        not null unique,
    mime_type   varchar(64) not null,
    byte_size   bigint      not null check (byte_size > 0),
    width       int,                                      -- IMAGE
    height      int,                                      -- IMAGE
    duration_ms int,                                      -- AUDIO
    created_at  timestamptz not null default now(),
    created_by  bigint      references app_user (id)
);

create table media_translation (
    media_id      bigint      not null references media (id) on delete cascade,
    language_code varchar(16) not null references language (code),
    alt_text      text        not null,                   -- screen-reader description
    primary key (media_id, language_code)
);


-- -----------------------------------------------------------------------------
-- Reading passage, glosses, evidence
-- gloss/evidence rows are synced from body_html by the server on every save.
-- -----------------------------------------------------------------------------

create table reading_passage (
    exercise_id bigint primary key references exercise (id) on delete cascade,
    body_html   text   not null,              -- sanitized; <span class="w" data-g="gN">, <span class="ev" id="eN">
    word_count  int    not null default 0 check (word_count >= 0)
);

create table passage_gloss (
    id          bigint generated always as identity primary key,
    exercise_id bigint      not null references exercise (id) on delete cascade,
    gloss_key   varchar(16) not null,                     -- 'g7' = data-g in body_html
    surface     text        not null,                     -- 'eliminated'
    lemma       text,                                     -- 'eliminate'
    unique (exercise_id, gloss_key)
);

create table passage_gloss_translation (
    gloss_id      bigint      not null references passage_gloss (id) on delete cascade,
    language_code varchar(16) not null references language (code),
    meaning       text        not null,                   -- 'məhv edilmək'
    source        varchar(8)  not null default 'HUMAN' check (source in ('HUMAN', 'MACHINE')),
    reviewed      boolean     not null default false,
    updated_at    timestamptz not null default now(),
    primary key (gloss_id, language_code)
);

create table passage_evidence (
    id           bigint generated always as identity primary key,
    exercise_id  bigint      not null references exercise (id) on delete cascade,
    evidence_key varchar(16) not null,                    -- 'e4' = id="" in body_html
    unique (exercise_id, evidence_key),
    unique (exercise_id, id)                              -- target of question's same-exercise FK
);


-- -----------------------------------------------------------------------------
-- Questions. Numbers (1..N) are derived from positions and points, never stored.
-- -----------------------------------------------------------------------------

create table question_group (
    id               bigint generated always as identity primary key,
    exercise_id      bigint      not null references exercise (id) on delete cascade,
    position         int         not null,
    type_code        varchar(32) not null references question_type (code),
    instruction_html text        not null,                -- official instruction, target language
    choose_count     int         check (choose_count > 0),  -- mcMulti "Choose TWO" -> 2 = points per question
    max_words        int         check (max_words > 0),     -- gap "NO MORE THAN TWO WORDS" -> 2
    numbers_allowed  boolean     not null default false,    -- gap "... AND/OR A NUMBER"
    options_reusable boolean     not null default false,    -- match "any letter more than once"
    media_id         bigint      references media (id),     -- DIAGRAM_LABEL picture
    unique (exercise_id, position),
    unique (exercise_id, id)                              -- target of question's same-exercise FK
);
create index question_group_type_idx on question_group (type_code, exercise_id);  -- filter by question type

create table question_group_translation (
    group_id         bigint      not null references question_group (id) on delete cascade,
    language_code    varchar(16) not null references language (code),
    instruction_html text        not null,                -- helper translation beside the official one
    primary key (group_id, language_code)
);

create table question (
    id          bigint generated always as identity primary key,
    exercise_id bigint not null references exercise (id) on delete cascade,
    group_id    bigint not null,
    position    int    not null,                          -- within the group
    stem_text   text,                                     -- statement / question / prompt
    stem_pre    text,                                     -- gap: before the blank
    stem_post   text,                                     -- gap: after the blank
    evidence_id bigint,
    unique (group_id, position),
    -- Group and evidence must belong to the SAME exercise as the question.
    -- NO ACTION (not RESTRICT) on evidence: checked at end of statement, so an
    -- exercise cascade-delete that removes both sides still works.
    constraint question_group_same_exercise_fk
        foreign key (exercise_id, group_id) references question_group (exercise_id, id) on delete cascade,
    constraint question_evidence_same_exercise_fk
        foreign key (exercise_id, evidence_id) references passage_evidence (exercise_id, id),
    constraint question_stem_check
        check (stem_text is not null or stem_pre is not null)
);
create index question_evidence_idx on question (exercise_id, evidence_id) where evidence_id is not null;

create table question_option (
    id          bigint generated always as identity primary key,
    group_id    bigint references question_group (id) on delete cascade, -- match: shared bank
    question_id bigint references question (id) on delete cascade,       -- MCQ: own options
    option_key  varchar(8) not null,                      -- 'A', 'i'
    label       text       not null,                      -- target language
    position    int        not null,
    constraint question_option_one_owner_check
        check ((group_id is null) <> (question_id is null)),
    unique (group_id, option_key),
    unique (question_id, option_key)
);

create table question_answer (                            -- never read by the learner endpoint
    id          bigint generated always as identity primary key,
    question_id bigint not null references question (id) on delete cascade,
    value       text   not null,                          -- 'FALSE' | 'riverbanks' | 'B'
    position    int    not null,
    unique (question_id, value)
);

create table question_explanation (                       -- never read by the learner endpoint before submit
    question_id      bigint      not null references question (id) on delete cascade,
    language_code    varchar(16) not null references language (code),
    explanation_html text        not null,
    source           varchar(8)  not null default 'HUMAN' check (source in ('HUMAN', 'MACHINE')),
    reviewed         boolean     not null default false,
    updated_at       timestamptz not null default now(),
    primary key (question_id, language_code)
);


-- -----------------------------------------------------------------------------
-- Attempts (history; never rewritten by later content edits)
-- -----------------------------------------------------------------------------

create table attempt (
    id                uuid        primary key default gen_random_uuid(),
    user_id           bigint      references app_user (id),   -- null = anonymous
    exercise_id       bigint      not null,
    exercise_revision int         not null,
    support_language  varchar(16) not null references language (code),
    started_at        timestamptz,
    submitted_at      timestamptz not null default now(),
    elapsed_seconds   int         not null check (elapsed_seconds >= 0),
    score             int         not null check (score >= 0),  -- sum of points_awarded
    total             int         not null check (total >= 0),  -- sum of points_possible
    constraint attempt_exercise_fk                        -- an exercise with attempts can't be deleted (archive it)
        foreign key (exercise_id) references exercise (id) on delete restrict,
    constraint attempt_score_within_total_check check (score <= total)
);
create index attempt_user_idx     on attempt (user_id, submitted_at desc) where user_id is not null;
create index attempt_exercise_idx on attempt (exercise_id, submitted_at desc);

create table attempt_answer (
    attempt_id      uuid    not null references attempt (id) on delete cascade,
    question_id     bigint  not null references question (id) on delete restrict,
    question_number int     not null check (question_number > 0), -- first n shown (7 for "Q7–8")
    given_value     jsonb   not null,                     -- "FALSE" | "riverbanks" | ["A","C"]
    expected_value  jsonb   not null,                     -- answer snapshot at grading time
    points_awarded  int     not null check (points_awarded >= 0),
    points_possible int     not null check (points_possible > 0),
    is_correct      boolean generated always as (points_awarded = points_possible) stored,
    constraint attempt_answer_points_check check (points_awarded <= points_possible),
    primary key (attempt_id, question_id)
);
create index attempt_answer_question_idx on attempt_answer (question_id);
