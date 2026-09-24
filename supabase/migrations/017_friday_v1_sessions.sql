-- FridayV1 Step 1: Friday Sessions table
create table if not exists friday_sessions (
  id uuid primary key default gen_random_uuid(),
  owner_id text not null references profiles(id),
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  summary text,
  status text not null default 'active' check (status in ('active','ended'))
);
create index if not exists idx_friday_sessions_owner_status on friday_sessions(owner_id, status);
alter table friday_sessions enable row level security;
