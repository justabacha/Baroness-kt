-- FridayV1 Step 5: Atomic API usage increment RPC
create or replace function increment_api_usage(p_owner_id text, p_provider text, p_day date default current_date)
returns integer as $$
declare
  v_count integer;
begin
  insert into friday_api_usage (owner_id, provider, day, call_count)
  values (p_owner_id, p_provider, p_day, 1)
  on conflict (owner_id, provider, day)
  do update set call_count = friday_api_usage.call_count + 1
  returning call_count into v_count;

  return v_count;
end;
$$ language plpgsql security definer;
