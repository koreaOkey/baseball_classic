create index if not exists idx_user_theme_purchases_theme_id
  on public.user_theme_purchases(theme_id);

create index if not exists idx_user_theme_settings_active_theme_id
  on public.user_theme_settings(active_theme_id);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop policy if exists "user_read_own_theme_purchases" on public.user_theme_purchases;
drop policy if exists "user_insert_own_theme_purchases" on public.user_theme_purchases;

drop policy if exists "user_read_own_theme_settings" on public.user_theme_settings;
drop policy if exists "user_insert_own_theme_settings" on public.user_theme_settings;
drop policy if exists "user_update_own_theme_settings" on public.user_theme_settings;

create policy "user_read_own_theme_purchases"
on public.user_theme_purchases
for select
to authenticated
using ((select auth.uid()) = user_id);

create policy "user_insert_own_theme_purchases"
on public.user_theme_purchases
for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy "user_read_own_theme_settings"
on public.user_theme_settings
for select
to authenticated
using ((select auth.uid()) = user_id);

create policy "user_insert_own_theme_settings"
on public.user_theme_settings
for insert
to authenticated
with check ((select auth.uid()) = user_id);

create policy "user_update_own_theme_settings"
on public.user_theme_settings
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);
