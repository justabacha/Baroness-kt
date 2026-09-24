-- FridayV1 Step 1: Memory History table
create table if not exists friday_memory_history (
  id uuid primary key default gen_random_uuid(),
  memory_id bigint not null references friday_memories(id) on delete cascade,
  change_type text not null check (change_type in ('created','reinforced','updated','superseded','archived')),
  previous_text text,
  reason text,
  changed_at timestamptz not null default now()
);
alter table friday_memory_history enable row level security;
-- No client-facing policy — service role only (Supabase service_role bypasses RLS by default).
