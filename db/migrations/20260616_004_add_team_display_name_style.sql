do $$
begin
  if to_regclass('public.user_settings') is not null then
    alter table public.user_settings
      add column if not exists team_display_name_style text not null default 'TEAM';
  end if;

  if to_regclass('public.team_subscription_tokens') is not null then
    alter table public.team_subscription_tokens
      add column if not exists display_name_style text not null default 'TEAM';
  end if;

  if to_regclass('public.device_tokens') is not null then
    alter table public.device_tokens
      add column if not exists display_name_style text not null default 'TEAM';
  end if;
end $$;

do $$
begin
  if to_regclass('public.user_settings') is not null
     and not exists (
       select 1 from pg_constraint where conname = 'user_settings_team_display_name_style_chk'
     ) then
    alter table public.user_settings
      add constraint user_settings_team_display_name_style_chk
      check (team_display_name_style in ('TEAM', 'MASCOT'));
  end if;

  if to_regclass('public.team_subscription_tokens') is not null
     and not exists (
       select 1 from pg_constraint where conname = 'team_subscription_tokens_display_name_style_chk'
     ) then
    alter table public.team_subscription_tokens
      add constraint team_subscription_tokens_display_name_style_chk
      check (display_name_style in ('TEAM', 'MASCOT'));
  end if;

  if to_regclass('public.device_tokens') is not null
     and not exists (
       select 1 from pg_constraint where conname = 'device_tokens_display_name_style_chk'
     ) then
    alter table public.device_tokens
      add constraint device_tokens_display_name_style_chk
      check (display_name_style in ('TEAM', 'MASCOT'));
  end if;
end $$;
