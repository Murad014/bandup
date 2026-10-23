-- =============================================================================
-- V6 — failed sign-in counters (backend requirement 8.12). One row per key:
-- 'email:<address>' or 'ip:<address>'. After too many failures in a window the
-- key is locked until locked_until. Old rows are deleted by the daily cleanup.
-- =============================================================================

create table login_throttle (
    throttle_key      varchar(330) primary key,
    failures          int          not null check (failures >= 0),
    window_started_at timestamptz  not null,
    locked_until      timestamptz
);
create index login_throttle_window_idx on login_throttle (window_started_at);
