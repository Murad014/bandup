-- =============================================================================
-- Vocabulary: recall sessions (Azerbaijani → English, practice only).
-- Spec: .kiro/specs/vocabulary requirement 11, design §3.3.
-- =============================================================================

alter table vocab_session drop constraint vocab_session_kind_check;
alter table vocab_session add constraint vocab_session_kind_check check (kind in ('LEARN', 'REVIEW', 'RECALL'));
