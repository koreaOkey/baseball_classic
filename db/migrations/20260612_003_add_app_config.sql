create table if not exists public.app_config (
  id bigserial primary key,
  platform text not null,
  min_supported_version text not null default '',
  latest_version text not null default '',
  force_update boolean not null default false,
  update_title text not null default '업데이트가 필요합니다',
  update_message text not null default '안정적인 서비스 운영을 위해 최신 버전으로 업데이트해 주세요.',
  store_url text not null default '',
  notice_enabled boolean not null default false,
  notice_title text not null default '',
  notice_message text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_app_config_platform unique (platform),
  constraint app_config_platform_chk check (platform in ('ios', 'android', 'all'))
);

create index if not exists idx_app_config_platform
  on public.app_config (platform);

drop trigger if exists set_app_config_updated_at on public.app_config;
create trigger set_app_config_updated_at
before update on public.app_config
for each row
execute function public.set_updated_at();
