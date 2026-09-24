-- FridayV1 Step 1: Friday Action Requests table
create table if not exists friday_action_requests (
  id uuid primary key default gen_random_uuid(),
  owner_id text not null references profiles(id),
  session_id uuid references friday_sessions(id),
  action_name text not null,
  parameters jsonb not null,
  status text not null default 'dispatched' check (status in ('dispatched','executed','failed','rejected')),
  created_at timestamptz not null default now()
);
alter table friday_action_requests enable row level security;
