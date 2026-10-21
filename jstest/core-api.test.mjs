import { test } from "node:test";
import assert from "node:assert/strict";

import {
    formatClock,
    isLowTime,
    normalizeAnswer,
    gradeAnswer,
    gradeAttempt,
} from "../src/main/resources/static/js/reading/core.js";
import { SESSIONS } from "../src/main/resources/static/js/reading/fixtures.js";
import { stubApi } from "../src/main/resources/static/js/reading/api.js";

const KEY = SESSIONS["yellowstone-wolves"].answerKey;

test("formatClock renders mm:ss and overtime with a + prefix", () => {
    assert.equal(formatClock(300), "5:00");
    assert.equal(formatClock(65), "1:05");
    assert.equal(formatClock(0), "0:00");
    assert.equal(formatClock(-1), "+0:01");
    assert.equal(formatClock(-65), "+1:05");
});

test("isLowTime triggers at or below the threshold", () => {
    assert.equal(isLowTime(61, 60), false);
    assert.equal(isLowTime(60, 60), true);
    assert.equal(isLowTime(0, 60), true);
});

test("normalizeAnswer trims and lowercases", () => {
    assert.equal(normalizeAnswer("  RiverBanks "), "riverbanks");
    assert.equal(normalizeAnswer(null), "");
});

test("gradeAnswer: TFNG exact, FILL accepts any listed value, empty is wrong", () => {
    assert.equal(gradeAnswer({ answer: "FALSE" }, "FALSE"), true);
    assert.equal(gradeAnswer({ answer: "FALSE" }, "true"), false);
    assert.equal(gradeAnswer({ answer: ["riverbanks"] }, " Riverbanks "), true);
    assert.equal(gradeAnswer({ answer: ["wolves"] }, ""), false);
});

test("gradeAttempt scores a perfect attempt 6/6 at 100%", () => {
    const answers = [
        { n: 1, value: "FALSE" },
        { n: 2, value: "TRUE" },
        { n: 3, value: "NOT GIVEN" },
        { n: 4, value: "riverbanks" },
        { n: 5, value: "songbirds" },
        { n: 6, value: "wolves" },
    ];
    const r = gradeAttempt(KEY, answers);
    assert.equal(r.score, 6);
    assert.equal(r.total, 6);
    assert.equal(r.percent, 100);
    assert.equal(r.results.length, 6);
    assert.equal(r.results[0].answer, "FALSE");
    assert.equal(r.results[3].answer, "riverbanks");
});

test("gradeAttempt marks wrong/empty answers and rounds percent", () => {
    const answers = [
        { n: 1, value: "TRUE" }, // wrong
        { n: 2, value: "TRUE" },
        { n: 3, value: "NOT GIVEN" },
        { n: 4, value: "riverbanks" },
        { n: 5, value: "" }, // empty -> wrong
        { n: 6, value: "wolves" },
    ];
    const r = gradeAttempt(KEY, answers);
    assert.equal(r.score, 4);
    assert.equal(r.percent, 67);
    assert.equal(r.results[0].correct, false);
    assert.equal(r.results[4].correct, false);
});

test("stubApi.fetchSession returns the public session WITHOUT answers", async () => {
    const session = await stubApi.fetchSession("yellowstone-wolves");
    assert.ok(session.passageHtml.includes('class="w"'));
    assert.equal(session.questionGroups.length, 2);
    // No answer/explanation leakage anywhere in the public payload.
    const serialized = JSON.stringify(session);
    assert.equal(serialized.includes("answer"), false);
    assert.equal(serialized.includes("explanation"), false);
    session.questionGroups.forEach((g) =>
        g.questions.forEach((q) => {
            assert.equal("answer" in q, false);
            assert.equal("a" in q, false);
        })
    );
});

test("stubApi.fetchSession rejects unknown ids with 404", async () => {
    await assert.rejects(() => stubApi.fetchSession("does-not-exist"), (e) => e.status === 404);
});

test("stubApi.submitAttempt grades and echoes elapsed", async () => {
    const res = await stubApi.submitAttempt({
        passageId: "yellowstone-wolves",
        elapsedSeconds: 214,
        answers: [
            { n: 1, value: "FALSE" },
            { n: 2, value: "TRUE" },
            { n: 3, value: "NOT GIVEN" },
            { n: 4, value: "riverbanks" },
            { n: 5, value: "songbirds" },
            { n: 6, value: "wolves" },
        ],
    });
    assert.equal(res.score, 6);
    assert.equal(res.elapsedSeconds, 214);
    assert.equal(res.results[0].evidenceId, "e1");
    assert.ok(res.results[0].explanation.length > 0);
});
