-- 「动了没」数据库结构
-- 在 Supabase SQL Editor 中整段执行

-- 用户表：id 直接复用 Supabase Auth 的登录用户 id
create table if not exists public.users (
  id uuid primary key references auth.users(id) on delete cascade,
  nickname text not null default '运动新人' check (char_length(nickname) between 1 and 12),
  weight numeric(5,2) not null default 60 check (weight between 20 and 300),
  weekly_goal int not null default 150 check (weekly_goal between 1 and 2000),
  is_admin boolean not null default false,
  streak int not null default 0,
  last_check_date date,
  created_at timestamptz not null default now()
);

-- 兼容已建表：把昵称下限从 2 调整为 1（允许单字中文昵称）
alter table public.users drop constraint if exists users_nickname_check;
alter table public.users add constraint users_nickname_check check (char_length(nickname) between 1 and 12);

-- 兼容已建表：后台管理员标记
alter table public.users add column if not exists is_admin boolean not null default false;

-- 打卡记录：唯一索引兜底「同日同类型只能一条」
create table if not exists public.records (
  id bigserial primary key,
  user_id uuid not null references public.users(id) on delete cascade,
  date date not null,
  type text not null,
  duration int not null check (duration between 1 and 600),
  calories int not null check (calories >= 0),
  created_at timestamptz not null default now(),
  unique (user_id, date, type)
);

create index if not exists idx_records_date on public.records (date);
create index if not exists idx_records_user_date on public.records (user_id, date);

-- 周小结缓存
create table if not exists public.summaries (
  id bigserial primary key,
  user_id uuid not null references public.users(id) on delete cascade,
  week_start date not null,
  content text not null,
  regen_count int not null default 0,
  created_at timestamptz not null default now(),
  unique (user_id, week_start)
);

-- 兼容已建表：重新生成次数计数（每周节流用）
alter table public.summaries add column if not exists regen_count int not null default 0;

-- 本周排行榜视图：周一起算，按上海时区取当前周
create or replace view public.weekly_rank as
select u.id,
       u.nickname,
       sum(r.duration)::int as weekly_minutes
from public.users u
join public.records r on r.user_id = u.id
where r.date >= date_trunc('week', (now() at time zone 'Asia/Shanghai'))::date
group by u.id, u.nickname;

-- 三张表开 RLS 且不建公开策略：只允许服务端 service_role 访问
alter table public.users enable row level security;
alter table public.records enable row level security;
alter table public.summaries enable row level security;

-- 关键：视图默认以 owner 权限执行，会绕过基表 RLS，必须显式收紧
alter view public.weekly_rank set (security_invoker = true);

-- 显式收回 anon / authenticated 的全部权限，避免通过 PostgREST 直连
revoke all on public.users, public.records, public.summaries from anon, authenticated;
revoke all on public.weekly_rank from anon, authenticated;

-- 显式给 service_role 授权，保证服务端 secret key 可以读写
grant usage on schema public to service_role;
grant all on public.users, public.records, public.summaries to service_role;
grant select on public.weekly_rank to service_role;
grant usage, select on all sequences in schema public to service_role;
