-- =============================================================================
-- Vocabulary: sets replace the daily review and Recall.
-- Spec: .kiro/specs/vocabulary requirement 6, design §3.2–3.4, decisions 15–16.
-- Learned words are worked through 25 at a time in learned order; a word is
-- mastered after 2 rounds in a row right on the first try. Sets are computed
-- from learned_at, never stored. box / due_on / last_answered_on (the old
-- review) stay but are no longer read or written.
-- =============================================================================

alter table vocab_saved_word add column learned_at timestamptz;
alter table vocab_saved_word add column streak smallint not null default 0 check (streak between 0 and 2);
alter table vocab_saved_word add column rounds int not null default 0 check (rounds >= 0);

-- Words learned before sets keep the order they were saved in.
update vocab_saved_word set learned_at = created_at where state <> 'NEW';
update vocab_saved_word set streak = 2 where state = 'MASTERED';

-- A learned word has its learned time (it was: a learned word has a box).
alter table vocab_saved_word drop constraint vocab_saved_word_check;
alter table vocab_saved_word add constraint vocab_saved_word_learned_check check (state = 'NEW' or learned_at is not null);
create index vocab_saved_word_sets_idx on vocab_saved_word (user_id, learned_at, created_at, id) where state <> 'NEW';

-- Review and recall sessions are gone: their open sessions can't be answered any more.
delete from vocab_session where kind in ('REVIEW', 'RECALL');
alter table vocab_session drop constraint vocab_session_kind_check;
alter table vocab_session add constraint vocab_session_kind_check check (kind in ('LEARN', 'ROUND'));
