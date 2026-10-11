create or replace function public.custom_access_token_hook(event jsonb)
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
  claims jsonb := event -> 'claims';
  persona text := event #>> '{claims,app_metadata,persona}';
begin
  if claims is null or jsonb_typeof(claims) <> 'object' then
    raise exception 'Custom access token hook received no claims object';
  end if;

  if persona is null or btrim(persona) = '' then
    raise exception 'Auth user is missing app_metadata.persona';
  end if;

  claims := jsonb_set(claims, '{persona}', to_jsonb(persona), true);
  return jsonb_build_object('claims', claims);
end;
$$;

revoke all on function public.custom_access_token_hook(jsonb) from public, anon, authenticated;
grant usage on schema public to supabase_auth_admin;
grant execute on function public.custom_access_token_hook(jsonb) to supabase_auth_admin;
