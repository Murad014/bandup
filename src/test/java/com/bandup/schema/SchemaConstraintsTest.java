package com.bandup.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bandup.TestcontainersConfiguration;

/**
 * Proves that V1 enforces the rules from design §4 at the database level, so a
 * bug or a manual SQL fix can't store impossible data. Each test runs in a
 * transaction that is rolled back, so tests don't see each other's rows.
 * Languages and question types come from the V2 seed.
 */
@JdbcTest
@Import(TestcontainersConfiguration.class)
class SchemaConstraintsTest {

    @Autowired
    JdbcTemplate jdbc;

    // ---------------------------------------------------------------- schema

    @Test
    void createsAll30Tables() {
        Integer tables = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name <> 'flyway_schema_history'
                """, Integer.class);
        assertThat(tables).isEqualTo(30); // 28 + login_throttle (V6) + account_token (V8)
    }

    // ------------------------------------------------- V5: ids from the app

    @Test
    void exerciseGroupAndQuestionAcceptIdsTakenFromTheirSequence() {
        long exerciseId = nextId("exercise");
        long groupId = nextId("question_group");
        long questionId = nextId("question");

        jdbc.update("""
                insert into exercise (id, slug, skill, target_language, title)
                values (?, 'chosen-ids', 'READING', 'en', 'Chosen ids')""", exerciseId);
        jdbc.update("""
                insert into question_group (id, exercise_id, position, type_code, instruction_html)
                values (?, ?, 1, 'TFNG', 'Do the statements agree?')""", groupId, exerciseId);
        jdbc.update("""
                insert into question (id, exercise_id, group_id, position, stem_text)
                values (?, ?, ?, 1, 'Wolves returned.')""", questionId, exerciseId, groupId);

        assertThat(jdbc.queryForObject("select id from question where stem_text = 'Wolves returned.'", Long.class))
                .isEqualTo(questionId);
        // Rows without an id still get one from the same sequence, never a used one.
        assertThat(exercise("no-id-given", null, null)).isGreaterThan(exerciseId);
    }

    @Test
    void otherTablesStillRejectChosenIds() {
        assertThatThrownBy(() -> jdbc.update("insert into topic (id, slug) values (999, 'chosen')"))
                .isInstanceOf(org.springframework.jdbc.BadSqlGrammarException.class)
                .rootCause().hasMessageContaining("GENERATED ALWAYS");
    }

    private long nextId(String table) {
        return jdbc.queryForObject("select nextval(pg_get_serial_sequence(?, 'id'))", Long.class, table);
    }

    // -------------------------------------------------------------- exercise

    @Test
    void acceptsExerciseWithoutTimer() {
        long id = exercise("present-perfect-1", null, null);
        assertThat(jdbc.queryForObject("select duration_seconds from exercise where id = ?", Integer.class, id)).isNull();
    }

    @Test
    void rejectsLowTimeThresholdLongerThanDuration() {
        assertThatThrownBy(() -> exercise("wolves", 300, 400)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("exercise_low_time_check");
    }

    @Test
    void rejectsLowTimeThresholdWithoutTimer() {
        assertThatThrownBy(() -> exercise("wolves", null, 60)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("exercise_low_time_check");
    }

    @Test
    void rejectsStagePositionWithoutStage() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into exercise (slug, skill, target_language, title, position)
                values ('wolves', 'READING', 'en', 'The Return of the Wolves', 4)
                """)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("exercise_stage_position_check");
    }

    @Test
    void rejectsDuplicateSlug() {
        exercise("wolves", 300, 60);
        assertThatThrownBy(() -> exercise("wolves", 300, 60)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownLevel() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into exercise (slug, skill, target_language, title, level)
                values ('wolves', 'READING', 'en', 'Wolves', 'B9')
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------- options & questions

    @Test
    void rejectsOptionWithTwoOwners() {
        long ex = exercise("wolves", 300, 60);
        long group = group(ex, 1, "MCQ");
        long q = question(ex, group, 1, null);
        assertThatThrownBy(() -> jdbc.update(
                "insert into question_option (group_id, question_id, option_key, label, position) values (?, ?, 'A', 'disease', 1)",
                group, q)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("question_option_one_owner_check");
    }

    @Test
    void rejectsOptionWithNoOwner() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into question_option (option_key, label, position) values ('A', 'disease', 1)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("question_option_one_owner_check");
    }

    @Test
    void rejectsDuplicateOptionKeyForSameQuestion() {
        long ex = exercise("wolves", 300, 60);
        long q = question(ex, group(ex, 1, "MCQ"), 1, null);
        jdbc.update("insert into question_option (question_id, option_key, label, position) values (?, 'A', 'disease', 1)", q);
        assertThatThrownBy(() -> jdbc.update(
                "insert into question_option (question_id, option_key, label, position) values (?, 'A', 'floods', 2)", q))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsQuestionInGroupOfAnotherExercise() {
        long wolves = exercise("wolves", 300, 60);
        long coral = exercise("coral-reefs", 1200, 120);
        long coralGroup = group(coral, 1, "TFNG");
        assertThatThrownBy(() -> question(wolves, coralGroup, 1, null)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("question_group_same_exercise_fk");
    }

    @Test
    void rejectsEvidenceFromAnotherExercise() {
        long wolves = exercise("wolves", 300, 60);
        long coral = exercise("coral-reefs", 1200, 120);
        long coralEvidence = evidence(coral, "e1");
        long wolvesGroup = group(wolves, 1, "TFNG");
        assertThatThrownBy(() -> question(wolves, wolvesGroup, 1, coralEvidence)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("question_evidence_same_exercise_fk");
    }

    @Test
    void blocksDeletingEvidenceStillUsedByAQuestion() {
        long ex = exercise("wolves", 300, 60);
        long e1 = evidence(ex, "e1");
        question(ex, group(ex, 1, "TFNG"), 1, e1);
        assertThatThrownBy(() -> jdbc.update("delete from passage_evidence where id = ?", e1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("question_evidence_same_exercise_fk");
    }

    @Test
    void rejectsZeroWordLimit() {
        long ex = exercise("wolves", 300, 60);
        assertThatThrownBy(() -> jdbc.update(
                "insert into question_group (exercise_id, position, type_code, instruction_html, max_words) values (?, 1, 'TFNG', 'x', 0)", ex))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------- deletes

    @Test
    void deletingExerciseWithoutAttemptsRemovesAllItsContent() {
        long ex = exercise("wolves", 300, 60);
        long e1 = evidence(ex, "e1");
        long q = question(ex, group(ex, 1, "TFNG"), 1, e1);
        jdbc.update("insert into question_answer (question_id, value, position) values (?, 'FALSE', 1)", q);

        jdbc.update("delete from exercise where id = ?", ex);

        assertThat(countFor("question", ex)).isZero();
        assertThat(countFor("question_group", ex)).isZero();
        assertThat(countFor("passage_evidence", ex)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from question_answer where question_id = ?", Integer.class, q)).isZero();
    }

    @Test
    void blocksDeletingExerciseWithAttempts() {
        long ex = exercise("wolves", 300, 60);
        attempt(ex, 5, 8);
        assertThatThrownBy(() -> jdbc.update("delete from exercise where id = ?", ex))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("attempt_exercise_fk");
    }

    // --------------------------------------------------------------- attempts

    @Test
    void isCorrectIsComputedFromPoints() {
        long ex = exercise("wolves", 300, 60);
        long group = group(ex, 1, "MCQ");
        long q7 = question(ex, group, 1, null);
        long q1 = question(ex, group, 2, null);
        String attempt = attempt(ex, 2, 3);
        answer(attempt, q7, 7, 1, 2);
        answer(attempt, q1, 1, 1, 1);

        assertThat(jdbc.queryForObject("select is_correct from attempt_answer where question_id = ?", Boolean.class, q7)).isFalse();
        assertThat(jdbc.queryForObject("select is_correct from attempt_answer where question_id = ?", Boolean.class, q1)).isTrue();
    }

    @Test
    void rejectsMorePointsThanPossible() {
        long ex = exercise("wolves", 300, 60);
        long q = question(ex, group(ex, 1, "MCQ"), 1, null);
        String attempt = attempt(ex, 0, 8);
        assertThatThrownBy(() -> answer(attempt, q, 7, 3, 2)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("attempt_answer_points_check");
    }

    @Test
    void rejectsScoreAboveTotal() {
        long ex = exercise("wolves", 300, 60);
        assertThatThrownBy(() -> attempt(ex, 7, 6)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("attempt_score_within_total_check");
    }

    // ------------------------------------------------------ languages & users

    @Test
    void rejectsSecondTranslationInSameLanguage() {
        long ex = exercise("wolves", 300, 60);
        long gloss = jdbc.queryForObject(
                "insert into passage_gloss (exercise_id, gloss_key, surface) values (?, 'g1', 'eliminated') returning id", Long.class, ex);
        jdbc.update("insert into passage_gloss_translation (gloss_id, language_code, meaning) values (?, 'az', 'məhv edilmək')", gloss);
        assertThatThrownBy(() -> jdbc.update(
                "insert into passage_gloss_translation (gloss_id, language_code, meaning) values (?, 'az', 'yox edilmək')", gloss))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownLanguage() {
        long ex = exercise("wolves", 300, 60);
        long gloss = jdbc.queryForObject(
                "insert into passage_gloss (exercise_id, gloss_key, surface) values (?, 'g1', 'eliminated') returning id", Long.class, ex);
        assertThatThrownBy(() -> jdbc.update(
                "insert into passage_gloss_translation (gloss_id, language_code, meaning) values (?, 'azz', 'x')", gloss))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUpperCaseEmail() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into app_user (email, ui_language, support_language) values ('Aysel@Example.com', 'az', 'az')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("app_user_email_lowercase_check");
    }

    // --------------------------------------------------------- login (V3)

    @Test
    void deletingAUserRemovesTheirCredentialAndRefreshTokens() {
        long user = user("aysel@example.com");
        jdbc.update("insert into user_credential (user_id, password_hash) values (?, '$2a$10$hash')", user);
        refreshToken(user, HASH_A, "now() + interval '14 days'");

        jdbc.update("delete from app_user where id = ?", user);

        assertThat(jdbc.queryForObject("select count(*) from user_credential where user_id = ?", Integer.class, user)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where user_id = ?", Integer.class, user)).isZero();
    }

    @Test
    void allowsOnlyOneCredentialPerUser() {
        long user = user("aysel@example.com");
        jdbc.update("insert into user_credential (user_id, password_hash) values (?, 'h1')", user);
        assertThatThrownBy(() -> jdbc.update("insert into user_credential (user_id, password_hash) values (?, 'h2')", user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsADuplicateRefreshTokenHash() {
        long aysel = user("aysel@example.com");
        long emre = user("emre@example.com");
        refreshToken(aysel, HASH_A, "now() + interval '14 days'");
        assertThatThrownBy(() -> refreshToken(emre, HASH_A, "now() + interval '14 days'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsARefreshTokenThatExpiresBeforeItIsIssued() {
        long user = user("aysel@example.com");
        assertThatThrownBy(() -> refreshToken(user, HASH_A, "now() - interval '1 minute'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("refresh_token_expiry_check");
    }

    @Test
    void rejectsARefreshTokenHashThatIsNotSha256Hex() {
        long user = user("aysel@example.com");
        assertThatThrownBy(() -> refreshToken(user, "not-a-hash", "now() + interval '14 days'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("refresh_token_hash_format_check");
    }

    @Test
    void rejectsAnUpperCaseRefreshTokenHash() {
        long user = user("aysel@example.com");
        assertThatThrownBy(() -> refreshToken(user, HASH_A.toUpperCase(), "now() + interval '14 days'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("refresh_token_hash_format_check");
    }

    @Test
    void rotationLinksTheOldTokenToTheNewOneInTheSameFamily() {
        long user = user("aysel@example.com");
        long old = refreshToken(user, HASH_A, "now() + interval '14 days'");
        long next = jdbc.queryForObject("""
                insert into refresh_token (user_id, token_hash, family_id, expires_at)
                select user_id, ?, family_id, now() + interval '14 days' from refresh_token where id = ?
                returning id
                """, Long.class, HASH_B, old);
        jdbc.update("update refresh_token set replaced_by = ? where id = ?", next, old);

        assertThat(jdbc.queryForObject(
                "select count(distinct family_id) from refresh_token where id in (?, ?)", Integer.class, old, next)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select replaced_by from refresh_token where id = ?", Long.class, old)).isEqualTo(next);
    }

    // ---------------------------------------------------------------- helpers

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    private long user(String email) {
        return jdbc.queryForObject(
                "insert into app_user (email, ui_language, support_language) values (?, 'az', 'az') returning id", Long.class, email);
    }

    private long refreshToken(long userId, String hash, String expiresAtSql) {
        return jdbc.queryForObject(
                "insert into refresh_token (user_id, token_hash, family_id, expires_at) values (?, ?, gen_random_uuid(), "
                        + expiresAtSql + ") returning id", Long.class, userId, hash);
    }

    private long exercise(String slug, Integer durationSeconds, Integer lowTimeThresholdSeconds) {
        return jdbc.queryForObject("""
                insert into exercise (slug, skill, target_language, title, duration_seconds, low_time_threshold_seconds)
                values (?, 'READING', 'en', ?, ?, ?) returning id
                """, Long.class, slug, slug, durationSeconds, lowTimeThresholdSeconds);
    }

    private long group(long exerciseId, int position, String type) {
        return jdbc.queryForObject("""
                insert into question_group (exercise_id, position, type_code, instruction_html)
                values (?, ?, ?, 'instruction') returning id
                """, Long.class, exerciseId, position, type);
    }

    private long question(long exerciseId, long groupId, int position, Long evidenceId) {
        return jdbc.queryForObject("""
                insert into question (exercise_id, group_id, position, stem_text, evidence_id)
                values (?, ?, ?, 'stem', ?) returning id
                """, Long.class, exerciseId, groupId, position, evidenceId);
    }

    private long evidence(long exerciseId, String key) {
        return jdbc.queryForObject(
                "insert into passage_evidence (exercise_id, evidence_key) values (?, ?) returning id", Long.class, exerciseId, key);
    }

    private String attempt(long exerciseId, int score, int total) {
        return jdbc.queryForObject("""
                insert into attempt (exercise_id, exercise_revision, support_language, elapsed_seconds, score, total)
                values (?, 1, 'az', 214, ?, ?) returning id::text
                """, String.class, exerciseId, score, total);
    }

    private void answer(String attemptId, long questionId, int number, int awarded, int possible) {
        jdbc.update("""
                insert into attempt_answer (attempt_id, question_id, question_number, given_value, expected_value, points_awarded, points_possible)
                values (?::uuid, ?, ?, '"x"'::jsonb, '"y"'::jsonb, ?, ?)
                """, attemptId, questionId, number, awarded, possible);
    }

    private int countFor(String table, long exerciseId) {
        return jdbc.queryForObject("select count(*) from " + table + " where exercise_id = ?", Integer.class, exerciseId);
    }
}
