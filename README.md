# 动了没

校园运动打卡全栈 Web App：记录运动、连续打卡、周榜排行，并用 AI 生成每周小结。

## 线上地址

- 用户端：<https://donglemei-lyf.netlify.app>
- 后台入口：<https://donglemei-lyf.netlify.app/admin/login>
- Supabase 代理：<https://donglemei-lyf.netlify.app/.netlify/functions/supabase-proxy>

推送到 GitHub `main` 分支会自动触发 Netlify 构建部署。

本地开发同样可访问 `http://127.0.0.1:8888`，前后端都已切到上面的代理地址。

## 技术栈

- 前端：Vue 3 + Vite + Vue Router + ECharts + Lucide 图标，手写设计系统（原生系统风 + 校园轻快），不使用 UI 框架
- 后端：Netlify Functions（Node.js Serverless）
- 数据库与身份：Supabase PostgreSQL + Supabase Auth
- 部署：Netlify
- PWA：vite-plugin-pwa

## 功能

- 单输入框自动识别手机号 / 邮箱，密码注册与登录
- 注册后引导设置昵称、体重、周目标
- 6 种运动打卡，按 MET 公式实时估算卡路里
- 连续打卡 streak、今日数据、本周进度
- 近 7 天 / 30 天 / 本月统计，柱状图 + 饼图
- 本周 Top 20 排行榜，支持“我的排名”
- 每周 AI 小结，支持缓存与重新生成
- 资料编辑、数据导出、清空数据、退出登录

## 本地启动

### 1. 安装依赖

需要 Node 20+。

```bash
npm install
```

### 2. 准备 Supabase

1. 在 [supabase.com](https://supabase.com) 创建项目。
2. 打开 SQL Editor，执行 [`supabase/schema.sql`](./supabase/schema.sql)。
3. 打开 Authentication → Providers，确认 **Email** 已启用。
4. **关闭 Confirm email**（演示阶段必须关闭，否则注册后拿不到 session，手机号映射邮箱也收不到验证信）。
5. 在 Project Settings → API Keys 中获取：
   - Project URL
   - publishable key（前端用，新版 `sb_publishable_...`）
   - secret key（仅后端用，新版 `sb_secret_...`）

### 3. 配置环境变量

```bash
copy .env.example .env
```

填写：

```env
VITE_SUPABASE_URL=https://xxxx.supabase.co
VITE_SUPABASE_PUBLISHABLE_KEY=sb_publishable_xxx

SUPABASE_URL=https://xxxx.supabase.co
SUPABASE_SECRET_KEY=sb_secret_xxx

LLM_API_URL=https://api.siliconflow.cn/v1/chat/completions
LLM_API_KEY=sk-xxx
LLM_MODEL=Qwen/Qwen2.5-7B-Instruct
```

> 旧版 anon key / service_role key 也兼容，填在 `VITE_SUPABASE_ANON_KEY` / `SUPABASE_SERVICE_ROLE_KEY` 即可。`VITE_` 前缀的变量会被打包进前端，secret key 千万不要加这个前缀。`.env` 已在 `.gitignore` 中。

### 4. 启动本地全栈

```bash
npm install
npx netlify login
npx netlify dev
```

访问 `http://localhost:8888`。`netlify dev` 会同时代理 Vite 前端与 Netlify Functions。也可以直接运行 `npm run dev:full`。

如果 `netlify dev` 在本机启动后端口无响应，用内置的本地全栈启动器：

```bash
npm run dev:local
```

它会先构建，再用 Node 直接托管 `dist` 并调用 `netlify/functions/*`，访问 `http://127.0.0.1:8888`。已有 `dist` 时可以用 `npm run start:local` 跳过构建。

只调前端样式时也可以 `npm run dev`，但接口不可用。

## 网络受限时：通过 Netlify 反向代理访问 Supabase

部分网络会重置对 `*.supabase.co` 的 TCP 连接（浏览器可能靠 HTTP/3 偶尔打开，但 Node 后端只走 TCP，接口仍然会失败）。此时可以把 Supabase 的 HTTP 入口挂到自己的 Netlify 域名下：

```text
浏览器 / 本地后端
      │  https://你的站点.netlify.app/.netlify/functions/supabase-proxy/auth/v1/...
      ▼
Netlify Function（supabase-proxy.js）
      │  转发到 https://你的项目.supabase.co/auth/v1/...
      ▼
Supabase
```

前端与后端都不再直连 `*.supabase.co`，只要网络能打开你的 Netlify 域名，就能正常登录和读写数据。

### 配置步骤

1. 在 Netlify 创建站点并部署本项目（见上一节）。
2. 在 Site settings → Environment variables 增加：

```bash
SUPABASE_PROJECT_URL=https://xxxx.supabase.co
```

3. 把下面两个变量改为代理地址（注意结尾不要加 `/auth/v1`）：

```bash
VITE_SUPABASE_URL=https://你的站点.netlify.app/.netlify/functions/supabase-proxy
SUPABASE_URL=https://你的站点.netlify.app/.netlify/functions/supabase-proxy
```

4. 本地 `.env` 使用同样的两个值并重新构建：`npm run dev:local`。
5. 代理只放行 `/auth/v1/*` 与 `/rest/v1/*`，其余路径返回 404；`apikey` 与 `Authorization` 会原样透传，secret key 不会出现在浏览器。
6. 切换到代理地址后，本地旧的登录态会失效（storage key 变化），重新登录一次即可。

> 直连模式仍然保留：网络没有限制时，把两个 URL 改回 `https://xxxx.supabase.co` 并删除 `SUPABASE_PROJECT_URL` 也不会影响业务代码。

## 部署到 Netlify

1. 把仓库推到 GitHub，在 Netlify 中导入。
2. 构建命令 `npm run build`，发布目录 `dist`（`netlify.toml` 已配置）。
3. 在 Site settings → Environment variables 中逐条配置线上环境变量：

```bash
netlify env:set SUPABASE_URL https://xxxx.supabase.co
netlify env:set SUPABASE_SECRET_KEY sb_secret_xxx
netlify env:set VITE_SUPABASE_URL https://xxxx.supabase.co
netlify env:set VITE_SUPABASE_PUBLISHABLE_KEY sb_publishable_xxx
netlify env:set LLM_API_URL https://api.siliconflow.cn/v1/chat/completions
netlify env:set LLM_API_KEY sk-xxx
```

4. 重新部署。`netlify.toml` 已包含 SPA fallback、Functions 目录与 CSP 安全头。

## 手机号登录说明

为了让课程项目零成本跑通，手机号只作为账号标识：不发送、不校验短信验证码。

登录页只有一个账号输入框，输入 11 位手机号或邮箱后自动识别：手机号会映射为 `手机号@phone.dlm.app`，复用 Supabase 的邮箱密码体系；登录时做同样映射。代价是无法验证手机号真实归属。要真实验证需接入 Twilio 等 SMS Provider 并改用 Supabase 原生 phone auth。

## 安全设计

- 密码由 Supabase Auth 保管，只存 bcrypt 哈希，后端不接触、不存储密码
- 后端函数使用 `SUPABASE_SECRET_KEY`，前端只使用 publishable key
- 三张业务表开启 RLS，`weekly_rank` 视图设置 `security_invoker = true`，并 revoke 了 anon / authenticated 权限
- 昵称使用白名单正则（中英文、数字、下划线、连字符），前端不使用 `v-html`
- `netlify.toml` 配置 CSP、`X-Content-Type-Options`、`Referrer-Policy`

## 目录结构

```text
.
├── src/
│   ├── pages/            # Auth / Onboarding / Home / Checkin / Stats / Rank / Summary / Mine
│   ├── utils/            # supabase.js / api.js / sport.js
│   ├── router/
│   ├── styles/
│   ├── App.vue
│   └── main.js
├── netlify/functions/    # db.js / checkin.js / stats.js / rank.js / summary.js / profile.js / admin.js / supabase-proxy.js
├── supabase/schema.sql
├── public/               # PWA 图标
├── netlify.toml
├── vite.config.js
└── .env.example
```

## 注意事项

- Supabase 免费项目连续约 7 天无请求会被暂停，演示前先打开后台唤醒。
- 同一天同一类型只能有一条记录，由数据库唯一索引兜底；重复提交时前端会弹窗确认覆盖。
- 所有日期按 `Asia/Shanghai` 生成，避免凌晨打卡被算到前一天。
