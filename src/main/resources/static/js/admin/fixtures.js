/* =========================================================================
   fixtures.js — seed data for the stub admin API. Admin objects are the FULL
   editable form, INCLUDING the answer key (unlike the learner read API).
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
    '<span class="ev" id="e2">In 1995, fourteen wolves were released into the park, and a further seventeen followed the next year</span>. ' +
    '<span class="ev" id="e5">As elk numbers fell, <span class="w" data-az="bitki örtüyü">vegetation</span> recovered, which provided ' +
    '<span class="w" data-az="yaşayış yeri">habitat</span> for <span class="w" data-az="qunduzlar">beavers</span> and ' +
    '<span class="w" data-az="oxuyan quşlar">songbirds</span></span>. ' +
    '<span class="ev" id="e6">Some ecologists point out that harsh winters and human hunting also reduced elk numbers, making it difficult to ' +
    '<span class="w" data-az="aid etmək">attribute</span> the changes to wolves alone</span>.';

export const SEED = [
    {
        id: "yellowstone-wolves",
        status: "published",
        title: "The Return of the Wolves",
        stageLabel: "Stage 1 · Mini passage 4",
        durationSeconds: 300,
        lowTimeThresholdSeconds: 60,
        passageHtml: yellowstonePassageHtml,
        questionGroups: [
            {
                type: "TFNG",
                heading: "Questions 1–3",
                instruction: "Do the statements agree with the information in the text? Choose <strong>TRUE</strong>, <strong>FALSE</strong> or <strong>NOT GIVEN</strong>.",
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
        answerKey: [
            { n: 1, answer: "FALSE", evidenceId: "e1", explanation: "<b>Mainly</b> = <i>largely</i>; the text says hunting, not disease." },
            { n: 2, answer: "TRUE", evidenceId: "e2", explanation: "14 then 17 — more the second year." },
            { n: 3, answer: "NOT GIVEN", evidenceId: "e2", explanation: "Tourists are never mentioned." },
            { n: 4, answer: ["riverbanks"], evidenceId: "e4", explanation: "<i>overgrazed … along riverbanks</i>." },
            { n: 5, answer: ["songbirds"], evidenceId: "e5", explanation: "<i>habitat for beavers and songbirds</i>." },
            { n: 6, answer: ["wolves"], evidenceId: "e6", explanation: "<i>attribute … to wolves alone</i>." },
        ],
    },
];
