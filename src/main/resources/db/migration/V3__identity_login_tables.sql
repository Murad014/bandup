-- =============================================================================
-- V3 — login tables for email/password sign-in with JWT (backend spec, R8).
-- Credentials live outside app_user so other sign-in methods (e.g. Google)
-- can be added later as their own tables.
-- =============================================================================

create table user_credential (
    user_id       bigint      primary key references app_user (id) on delete cascade,
    password_hash text        not null,                 -- BCrypt hash; never a raw password
    updated_at    timestamptz not null default now()
);

-- One row per issued refresh token. Rotation creates a new row in the same
-- family and points the old row at it; reuse of a replaced token revokes the
-- whole family. Only the SHA-256 hash of the cookie value is stored.
create table refresh_token (
    id          bigint generated always as identity primary key,
    user_id     bigint      not null references app_user (id) on delete cascade,
    token_hash  varchar(64) not null unique,             -- lower-case hex SHA-256
    family_id   uuid        not null,                    -- one login session
    issued_at   timestamptz not null default now(),
    expires_at  timestamptz not null,
    replaced_by bigint      references refresh_token (id) on delete set null,
    revoked_at  timestamptz,
    constraint refresh_token_expiry_check      check (expires_at > issued_at),
    constraint refresh_token_hash_format_check check (token_hash ~ '^[0-9a-f]{64}$')
);
create index refresh_token_user_idx   on refresh_token (user_id);
create index refresh_token_family_idx on refresh_token (family_id);
