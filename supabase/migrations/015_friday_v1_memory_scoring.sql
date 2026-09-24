-- FridayV1 Step 1: Memory Scoring columns and index
alter table friday_memories
  add column if not exists importance smallint not null default 3 check (importance between 1 and 5),
  add column if not exists confidence smallint not null default 3 check (confidence between 1 and 5),
  add column if not exists status text not null default 'active'
    check (status in ('candidate','validated','active','reinforced','updated','stale','archived')),
  add column if not exists last_reinforced_at timestamptz;

create index if not exists idx_friday_memories_owner_category_status
  on friday_memories(owner_id, category, status);
