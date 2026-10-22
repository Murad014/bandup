-- =============================================================================
-- V4 — simple English definitions for the Wolves glosses.
-- Every language's fallback chain ends at English, so these are the safety net
-- for learners whose language has no meaning yet. Drafted (not by a human
-- translator), hence source = 'MACHINE', reviewed = false until checked.
-- =============================================================================

insert into passage_gloss_translation (gloss_id, language_code, meaning, source, reviewed)
select g.id, 'en', v.meaning, 'MACHINE', false from (values
    ('g1',  'removed completely'),
    ('g2',  'paid for or supported by the government'),
    ('g3',  'an animal that hunts and eats other animals'),
    ('g4',  'a very large kind of deer'),
    ('g5',  'became bigger in number or size'),
    ('g6',  'ate too many of the plants in an area'),
    ('g7',  'a tree with long thin branches that often grows near water'),
    ('g8',  'a tall tree whose leaves shake in the wind'),
    ('g9',  'the land along the sides of a river'),
    ('g10', 'caught and taken'),
    ('g11', 'set free'),
    ('g12', 'the result of something'),
    ('g13', 'a chain of effects that starts at the top of a food chain and spreads down to other living things'),
    ('g14', 'a series of living things in which each one is eaten by the next'),
    ('g15', 'spread step by step to lower levels'),
    ('g16', 'all the plants and animals in an area and how they affect each other'),
    ('g17', 'plants in general'),
    ('g18', 'the natural home of an animal or plant'),
    ('g19', 'animals with flat tails that build dams in rivers'),
    ('g20', 'small birds that sing'),
    ('g21', 'however; even so'),
    ('g22', 'a story or explanation of events'),
    ('g23', 'questioned; said it may not be true'),
    ('g24', 'very cold or difficult'),
    ('g25', 'say that something is caused by something')
) v(gloss_key, meaning)
join passage_gloss g on g.gloss_key = v.gloss_key
                    and g.exercise_id = (select id from exercise where slug = 'yellowstone-wolves');
