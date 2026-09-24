-- FridayV1 Step 1: Friday API Usage table
create table if not exists friday_api_usage (
  owner_id text not null references profiles(id),
  provider text not null check (provider in ('groq','gemini')),
  day date not null default current_date,
  call_count integer not null default 0,
  primary key (owner_id, provider, day)
);
alter table friday_api_usage enable row level security;
