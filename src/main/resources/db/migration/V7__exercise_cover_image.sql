-- =============================================================================
-- V7 — a cover image per exercise (catalog cards, passage header). The image
-- itself is a `media` row (kind IMAGE; the file lives in file storage).
-- Without a cover, pages show an illustration for the exercise's topic.
-- =============================================================================

alter table exercise add column cover_media_id bigint references media (id) on delete set null;
