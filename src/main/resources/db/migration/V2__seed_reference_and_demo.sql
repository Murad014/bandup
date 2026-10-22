-- =============================================================================
-- V2 — reference data + the first published exercise.
-- Languages, question types and topics are reference data every environment
-- needs. "The Return of the Wolves" is BandUp's first live reading (it replaces
-- static/js/reading/fixtures.js once the learner API is wired up).
-- =============================================================================


-- -----------------------------------------------------------------------------
-- Languages. Only en + az are enabled; the rest are prepared but switched off.
-- Insert order matters: a fallback must exist before it is referenced.
-- -----------------------------------------------------------------------------

insert into language (code, name_en, name_native, direction, fallback_code, is_target, is_support, enabled, sort_order) values
    ('en', 'English',     'English',      'ltr', null, true,  true, true,  1),
    ('az', 'Azerbaijani', 'Azərbaycanca', 'ltr', 'en', false, true, true,  2),
    ('tr', 'Turkish',     'Türkçe',       'ltr', 'en', false, true, false, 3),
    ('ru', 'Russian',     'Русский',      'ltr', 'en', false, true, false, 4),
    ('uz', 'Uzbek',       'Oʻzbekcha',    'ltr', 'en', false, true, false, 5),
    ('kk', 'Kazakh',      'Қазақша',      'ltr', 'ru', false, true, false, 6),
    ('ar', 'Arabic',      'العربية',      'rtl', 'en', false, true, false, 7);


-- -----------------------------------------------------------------------------
-- Question types: the 11 from static/js/admin/questionTypes.js + 2 new ones.
-- -----------------------------------------------------------------------------

insert into question_type (code, family) values
    ('TFNG',           'choiceFixed'),
    ('YNNG',           'choiceFixed'),
    ('MCQ',            'mcSingle'),
    ('MCMA',           'mcMulti'),
    ('FILL',           'gap'),
    ('SUMMARY',        'gap'),
    ('SHORT',          'gap'),
    ('DIAGRAM_LABEL',  'gap'),
    ('MATCH_HEADINGS', 'match'),
    ('MATCH_INFO',     'match'),
    ('MATCH_FEATURES', 'match'),
    ('MATCH_ENDINGS',  'match'),
    ('SUMMARY_BOX',    'match');


-- -----------------------------------------------------------------------------
-- Topics (filters).
-- -----------------------------------------------------------------------------

insert into topic (slug, position) values
    ('nature', 1), ('science', 2), ('history', 3), ('society', 4), ('technology', 5);

insert into topic_translation (topic_id, language_code, name)
select t.id, v.lang, v.name from (values
    ('nature',     'en', 'Nature'),     ('nature',     'az', 'Təbiət'),
    ('science',    'en', 'Science'),    ('science',    'az', 'Elm'),
    ('history',    'en', 'History'),    ('history',    'az', 'Tarix'),
    ('society',    'en', 'Society'),    ('society',    'az', 'Cəmiyyət'),
    ('technology', 'en', 'Technology'), ('technology', 'az', 'Texnologiya')
) v(slug, lang, name)
join topic t on t.slug = v.slug;


-- -----------------------------------------------------------------------------
-- Course "IELTS Reading" with Stage 1 (optional grouping; nothing is locked).
-- -----------------------------------------------------------------------------

insert into course (slug, skill, target_language, position, status)
values ('ielts-reading', 'READING', 'en', 1, 'PUBLISHED');

insert into course_translation (course_id, language_code, title, description)
select c.id, v.lang, v.title, v.description from (values
    ('en', 'IELTS Reading', 'Practise every IELTS reading question type.'),
    ('az', 'IELTS Oxu',     'IELTS oxu sual növlərinin hamısını məşq edin.')
) v(lang, title, description)
join course c on c.slug = 'ielts-reading';

insert into stage (course_id, position)
select id, 1 from course where slug = 'ielts-reading';

insert into stage_translation (stage_id, language_code, title)
select s.id, v.lang, v.title from (values ('en', 'Stage 1'), ('az', 'Mərhələ 1')) v(lang, title)
join stage s on s.position = 1 and s.course_id = (select id from course where slug = 'ielts-reading');


-- -----------------------------------------------------------------------------
-- Exercise: "The Return of the Wolves" (mini reading, B1, topic nature).
-- -----------------------------------------------------------------------------

-- Generated from static/js/reading/fixtures.js (glosses converted data-az -> data-g + rows).
insert into exercise (slug, skill, target_language, stage_id, position, title, format, level,
                      duration_seconds, low_time_threshold_seconds, status, published_at)
values ('yellowstone-wolves', 'READING', 'en',
        (select s.id from stage s join course c on c.id = s.course_id where c.slug = 'ielts-reading' and s.position = 1),
        4, 'The Return of the Wolves', 'MINI', 'B1', 300, 60, 'PUBLISHED', now());

insert into exercise_topic (exercise_id, topic_id)
select (select id from exercise where slug = 'yellowstone-wolves'), id from topic where slug = 'nature';

insert into reading_passage (exercise_id, body_html, word_count)
values ((select id from exercise where slug = 'yellowstone-wolves'),
        '<span class="ev" id="e1">By the 1920s, grey wolves had been completely <span class="w" data-g="g1">eliminated</span> from Yellowstone National Park in the United States, largely through <span class="w" data-g="g2">government-sponsored</span> hunting</span>. Without their main <span class="w" data-g="g3">predator</span>, <span class="ev" id="e4"><span class="w" data-g="g4">elk</span> populations <span class="w" data-g="g5">expanded</span> rapidly and <span class="w" data-g="g6">overgrazed</span> young <span class="w" data-g="g7">willow</span> and <span class="w" data-g="g8">aspen</span> trees along <span class="w" data-g="g9">riverbanks</span></span>. <span class="ev" id="e2">In 1995, fourteen wolves <span class="w" data-g="g10">captured</span> in Canada were <span class="w" data-g="g11">released</span> into the park, and a further seventeen followed the next year</span>. Many scientists claim the <span class="w" data-g="g12">outcome</span> was a classic example of a "<span class="w" data-g="g13">trophic cascade</span>", in which changes at the top of a <span class="w" data-g="g14">food chain</span> <span class="w" data-g="g15">ripple down</span> to affect the entire <span class="w" data-g="g16">ecosystem</span>. <span class="ev" id="e5">As elk numbers fell and the animals avoided open valleys, <span class="w" data-g="g17">vegetation</span> recovered, which in turn provided <span class="w" data-g="g18">habitat</span> for <span class="w" data-g="g19">beavers</span> and <span class="w" data-g="g20">songbirds</span></span>. <span class="w" data-g="g21">Nevertheless</span>, this <span class="w" data-g="g22">narrative</span> has been <span class="w" data-g="g23">challenged</span>. <span class="ev" id="e6">Some ecologists point out that <span class="w" data-g="g24">harsh</span> winters, growing bear populations and human hunting also reduced elk numbers during the same period, making it difficult to <span class="w" data-g="g25">attribute</span> the changes to wolves alone</span>.',
        157);

insert into passage_evidence (exercise_id, evidence_key)
select (select id from exercise where slug = 'yellowstone-wolves'), k from (values ('e1'), ('e4'), ('e2'), ('e5'), ('e6')) v(k);

insert into passage_gloss (exercise_id, gloss_key, surface)
select (select id from exercise where slug = 'yellowstone-wolves'), v.k, v.surface from (values
    ('g1', 'eliminated'),
    ('g2', 'government-sponsored'),
    ('g3', 'predator'),
    ('g4', 'elk'),
    ('g5', 'expanded'),
    ('g6', 'overgrazed'),
    ('g7', 'willow'),
    ('g8', 'aspen'),
    ('g9', 'riverbanks'),
    ('g10', 'captured'),
    ('g11', 'released'),
    ('g12', 'outcome'),
    ('g13', 'trophic cascade'),
    ('g14', 'food chain'),
    ('g15', 'ripple down'),
    ('g16', 'ecosystem'),
    ('g17', 'vegetation'),
    ('g18', 'habitat'),
    ('g19', 'beavers'),
    ('g20', 'songbirds'),
    ('g21', 'Nevertheless'),
    ('g22', 'narrative'),
    ('g23', 'challenged'),
    ('g24', 'harsh'),
    ('g25', 'attribute')
) v(k, surface);

insert into passage_gloss_translation (gloss_id, language_code, meaning, source, reviewed)
select g.id, 'az', v.meaning, 'HUMAN', true from (values
    ('g1', 'məhv edilmək'),
    ('g2', 'dövlət tərəfindən dəstəklənən'),
    ('g3', 'yırtıcı'),
    ('g4', 'maral (böyük maral növü)'),
    ('g5', 'artmaq, genişlənmək'),
    ('g6', 'həddən artıq otlamaq'),
    ('g7', 'söyüd'),
    ('g8', 'titrək qovaq'),
    ('g9', 'çay sahilləri'),
    ('g10', 'tutulmaq'),
    ('g11', 'azad edilmək, buraxılmaq'),
    ('g12', 'nəticə'),
    ('g13', 'trofik kaskad (qida zəncirində zəncirvari təsir)'),
    ('g14', 'qida zənciri'),
    ('g15', 'dalğa kimi aşağı yayılmaq'),
    ('g16', 'ekosistem'),
    ('g17', 'bitki örtüyü'),
    ('g18', 'yaşayış yeri'),
    ('g19', 'qunduzlar'),
    ('g20', 'oxuyan quşlar'),
    ('g21', 'buna baxmayaraq'),
    ('g22', 'izah, hekayə'),
    ('g23', 'şübhə altına alınmaq'),
    ('g24', 'sərt'),
    ('g25', 'aid etmək, hesabına yazmaq')
) v(k, meaning)
join passage_gloss g on g.gloss_key = v.k and g.exercise_id = (select id from exercise where slug = 'yellowstone-wolves');

insert into question_group (exercise_id, position, type_code, instruction_html, max_words, numbers_allowed)
values
    ((select id from exercise where slug = 'yellowstone-wolves'), 1, 'TFNG', 'Do the statements agree with the information in the text? Choose <strong>TRUE</strong>, <strong>FALSE</strong> or <strong>NOT GIVEN</strong>.', null, false),
    ((select id from exercise where slug = 'yellowstone-wolves'), 2, 'FILL', 'Complete the sentences. Write <strong>ONE WORD ONLY</strong> from the text.', 1, false);

insert into question (exercise_id, group_id, position, stem_text, stem_pre, stem_post, evidence_id)
select g.exercise_id, g.id, v.pos, v.stem_text, v.stem_pre, v.stem_post, ev.id from (values
    (1, 1, 'Wolves disappeared from Yellowstone mainly because of disease.', null, null, 'e1'),
    (1, 2, 'More wolves were released in the second year than in the first.', null, null, 'e2'),
    (1, 3, 'The number of tourists visiting Yellowstone increased after the wolves returned.', null, null, 'e2'),
    (2, 1, null, 'Without wolves, too many elk damaged young trees near', '.', 'e4'),
    (2, 2, null, 'The recovery of plants created homes for beavers and', '.', 'e5'),
    (2, 3, null, 'Critics say it is hard to give credit for the changes only to', '.', 'e6')
) v(gpos, pos, stem_text, stem_pre, stem_post, ekey)
join question_group g on g.exercise_id = (select id from exercise where slug = 'yellowstone-wolves') and g.position = v.gpos
left join passage_evidence ev on ev.exercise_id = g.exercise_id and ev.evidence_key = v.ekey;

insert into question_answer (question_id, value, position)
select q.id, v.value, v.apos from (values
    (1, 1, 'FALSE', 1),
    (1, 2, 'TRUE', 1),
    (1, 3, 'NOT GIVEN', 1),
    (2, 1, 'riverbanks', 1),
    (2, 2, 'songbirds', 1),
    (2, 3, 'wolves', 1)
) v(gpos, qpos, value, apos)
join question_group g on g.exercise_id = (select id from exercise where slug = 'yellowstone-wolves') and g.position = v.gpos
join question q on q.group_id = g.id and q.position = v.qpos;

insert into question_explanation (question_id, language_code, explanation_html, source, reviewed)
select q.id, 'en', v.html, 'HUMAN', true from (values
    (1, 1, '<b>Mainly</b> = <i>largely</i>. The text gives the main reason: <b>hunting</b>, not disease. The text names a different cause, so it disagrees → FALSE.'),
    (1, 2, 'First year (1995): <b>14</b>. Next year: <b>17</b>. 17 is more than 14 → TRUE. IELTS often hides a comparison inside numbers, so you have to do the small calculation yourself.'),
    (1, 3, 'The text talks about elk, trees, beavers and birds, but <b>tourists</b> are never mentioned. Don''t use your own knowledge → NOT GIVEN.'),
    (2, 1, '<b>Damaged by eating too much</b> = <i>overgrazed</i>; <b>near</b> = <i>along</i>. After ''near'' you need a place: <b>riverbanks</b> (one word, with the s).'),
    (2, 2, '<b>Plants</b> = <i>vegetation</i>; <b>homes</b> = <i>habitat</i>. The pair is <i>beavers and songbirds</i>.'),
    (2, 3, '<b>Give credit to</b> = <i>attribute to</i>; <b>only</b> = <i>alone</i>. Answer: <b>wolves</b>. Trap: ''bears'' and ''winters'' are in the same sentence, but they are the <i>other</i> causes.')
) v(gpos, qpos, html)
join question_group g on g.exercise_id = (select id from exercise where slug = 'yellowstone-wolves') and g.position = v.gpos
join question q on q.group_id = g.id and q.position = v.qpos;
