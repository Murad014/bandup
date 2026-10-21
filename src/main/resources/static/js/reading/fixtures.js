/* =========================================================================
   fixtures.js — local stand-in for backend content, so the UI can be built
   and tested before the real REST API exists.

   Each entry has:
     - session:   the PUBLIC payload returned by GET /api/v1/passages/{id}
                  (passage HTML + questions, NEVER answers/explanations)
     - answerKey: the PRIVATE grading data the stub server uses for
                  POST /api/v1/attempts (answers + explanations + evidence).
   The split mirrors the real security boundary: answers stay "server-side".
   ========================================================================= */

const yellowstonePassageHtml =
    '<span class="ev" id="e1">By the 1920s, grey wolves had been completely ' +
    '<span class="w" data-az="məhv edilmək">eliminated</span> from Yellowstone National Park in the United States, largely through ' +
    '<span class="w" data-az="dövlət tərəfindən dəstəklənən">government-sponsored</span> hunting</span>. ' +
    'Without their main <span class="w" data-az="yırtıcı">predator</span>, ' +
    '<span class="ev" id="e4"><span class="w" data-az="maral (böyük maral növü)">elk</span> populations ' +
    '<span class="w" data-az="artmaq, genişlənmək">expanded</span> rapidly and ' +
    '<span class="w" data-az="həddən artıq otlamaq">overgrazed</span> young ' +
    '<span class="w" data-az="söyüd">willow</span> and <span class="w" data-az="titrək qovaq">aspen</span> trees along ' +
    '<span class="w" data-az="çay sahilləri">riverbanks</span></span>. ' +
    '<span class="ev" id="e2">In 1995, fourteen wolves <span class="w" data-az="tutulmaq">captured</span> in Canada were ' +
    '<span class="w" data-az="azad edilmək, buraxılmaq">released</span> into the park, and a further seventeen followed the next year</span>. ' +
    'Many scientists claim the <span class="w" data-az="nəticə">outcome</span> was a classic example of a "' +
    '<span class="w" data-az="trofik kaskad (qida zəncirində zəncirvari təsir)">trophic cascade</span>", in which changes at the top of a ' +
    '<span class="w" data-az="qida zənciri">food chain</span> <span class="w" data-az="dalğa kimi aşağı yayılmaq">ripple down</span> to affect the entire ' +
    '<span class="w" data-az="ekosistem">ecosystem</span>. ' +
    '<span class="ev" id="e5">As elk numbers fell and the animals avoided open valleys, ' +
    '<span class="w" data-az="bitki örtüyü">vegetation</span> recovered, which in turn provided ' +
    '<span class="w" data-az="yaşayış yeri">habitat</span> for <span class="w" data-az="qunduzlar">beavers</span> and ' +
    '<span class="w" data-az="oxuyan quşlar">songbirds</span></span>. ' +
    '<span class="w" data-az="buna baxmayaraq">Nevertheless</span>, this <span class="w" data-az="izah, hekayə">narrative</span> has been ' +
    '<span class="w" data-az="şübhə altına alınmaq">challenged</span>. ' +
    '<span class="ev" id="e6">Some ecologists point out that <span class="w" data-az="sərt">harsh</span> winters, ' +
    'growing bear populations and human hunting also reduced elk numbers during the same period, making it difficult to ' +
    '<span class="w" data-az="aid etmək, hesabına yazmaq">attribute</span> the changes to wolves alone</span>.';

const yellowstone = {
    session: {
        id: "yellowstone-wolves",
        stageLabel: "Stage 1 · Mini passage 4",
        title: "The Return of the Wolves",
        durationSeconds: 300,
        lowTimeThresholdSeconds: 60,
        passageHtml: yellowstonePassageHtml,
        questionGroups: [
            {
                type: "TFNG",
                heading: "Questions 1–3",
                instruction:
                    "Do the statements agree with the information in the text? Choose " +
                    "<strong>TRUE</strong>, <strong>FALSE</strong> or <strong>NOT GIVEN</strong>.",
                questions: [
                    { n: 1, type: "TFNG", text: "Wolves disappeared from Yellowstone mainly because of disease." },
                    { n: 2, type: "TFNG", text: "More wolves were released in the second year than in the first." },
                    { n: 3, type: "TFNG", text: "The number of tourists visiting Yellowstone increased after the wolves returned." },
                ],
            },
            {
                type: "FILL",
                heading: "Questions 4–6",
                instruction: "Complete the sentences. Write <strong>ONE WORD ONLY</strong> from the text.",
                questions: [
                    { n: 4, type: "FILL", pre: "Without wolves, too many elk damaged young trees near", post: "." },
                    { n: 5, type: "FILL", pre: "The recovery of plants created homes for beavers and", post: "." },
                    { n: 6, type: "FILL", pre: "Critics say it is hard to give credit for the changes only to", post: "." },
                ],
            },
        ],
    },

    answerKey: {
        1: {
            answer: "FALSE",
            evidenceId: "e1",
            explanation:
                "<b>Mainly</b> = <i>largely</i>. The text gives the main reason: <b>hunting</b>, not disease. " +
                "The text names a different cause, so it disagrees → FALSE.",
        },
        2: {
            answer: "TRUE",
            evidenceId: "e2",
            explanation:
                "First year (1995): <b>14</b>. Next year: <b>17</b>. 17 is more than 14 → TRUE. " +
                "IELTS often hides a comparison inside numbers, so you have to do the small calculation yourself.",
        },
        3: {
            answer: "NOT GIVEN",
            evidenceId: "e2",
            explanation:
                "The text talks about elk, trees, beavers and birds, but <b>tourists</b> are never mentioned. " +
                "Don't use your own knowledge → NOT GIVEN.",
        },
        4: {
            answer: ["riverbanks"],
            evidenceId: "e4",
            explanation:
                "<b>Damaged by eating too much</b> = <i>overgrazed</i>; <b>near</b> = <i>along</i>. " +
                "After 'near' you need a place: <b>riverbanks</b> (one word, with the s).",
        },
        5: {
            answer: ["songbirds"],
            evidenceId: "e5",
            explanation:
                "<b>Plants</b> = <i>vegetation</i>; <b>homes</b> = <i>habitat</i>. The pair is <i>beavers and songbirds</i>.",
        },
        6: {
            answer: ["wolves"],
            evidenceId: "e6",
            explanation:
                "<b>Give credit to</b> = <i>attribute to</i>; <b>only</b> = <i>alone</i>. Answer: <b>wolves</b>. " +
                "Trap: 'bears' and 'winters' are in the same sentence, but they are the <i>other</i> causes.",
        },
    },
};

export const SESSIONS = {
    "yellowstone-wolves": yellowstone,
};
