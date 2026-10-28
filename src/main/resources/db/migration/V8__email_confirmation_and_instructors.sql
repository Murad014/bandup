-- =============================================================================
-- V8 — email confirmation, password reset and Instructor requests
-- (backend requirement 12).
-- =============================================================================

-- Signing in needs a confirmed email. Accounts that exist already were made
-- before confirmation existed: they count as confirmed.
alter table app_user add column email_verified_at timestamptz;
update app_user set email_verified_at = created_at;

-- Sign-up as an Instructor is a request an admin approves (AUTHOR role) or rejects.
alter table app_user add column instructor_status varchar(16) not null default 'NONE'
    check (instructor_status in ('NONE', 'PENDING', 'APPROVED', 'REJECTED'));
alter table app_user add column instructor_requested_at timestamptz;
alter table app_user add constraint app_user_instructor_request_time_check
    check (instructor_status = 'NONE' or instructor_requested_at is not null);
create index app_user_pending_instructor_idx on app_user (instructor_requested_at) where instructor_status = 'PENDING';

-- Emailed one-time links. Only the SHA-256 hash of the link's secret is stored.
create table account_token (
    id         bigint generated always as identity primary key,
    user_id    bigint      not null references app_user (id) on delete cascade,
    purpose    varchar(24) not null check (purpose in ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    token_hash varchar(64) not null unique check (token_hash ~ '^[0-9a-f]{64}$'),
    issued_at  timestamptz not null,
    expires_at timestamptz not null,
    used_at    timestamptz,
    constraint account_token_expiry_check check (expires_at > issued_at)
);
create index account_token_user_purpose_idx on account_token (user_id, purpose);
