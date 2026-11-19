# 动了没

校园运动打卡全栈 Web App：记录运动、连续打卡、周榜排行，并用 AI 生成每周小结。

## 线上地址

- 用户端：<https://3.105.101.123.nip.io>
- 后台入口：<https://3.105.101.123.nip.io/admin/login>
- Supabase 代理：<https://3.105.101.123.nip.io/.netlify/functions/supabase-proxy>
- 实例：Ubuntu 24.04 / t3.small / 弹性 IP `3.105.101.123`
- SSH：`ssh -i D:\Edge\donglemei_new ubuntu@3.105.101.123`

本地开发同样可访问 `http://127.0.0.1:8888`，前后端都已切到 AWS 代理地址。

## 技术栈

- 前端：Vue 3 + Vite + Vue Router + ECharts + Lucide 图标，手写设计系统（原生系统风 + 校园轻快），不使用 UI 框架
- 后端：Node.js HTTP 服务，直接复用 `netlify/functions/` 下的 Handler
- 数据库与身份：Supabase PostgreSQL + Supabase Auth
- 部署：AWS EC2 + Caddy（自动 HTTPS）+ systemd
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

需要 Node 22+（supabase-js 需要原生 WebSocket）。

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
# 前端与后端统一走反向代理，避免网络直连 supabase.co 被重置
VITE_SUPABASE_URL=https://3.105.101.123.nip.io/.netlify/functions/supabase-proxy
VITE_SUPABASE_PUBLISHABLE_KEY=sb_publishable_xxx

SUPABASE_URL=https://3.105.101.123.nip.io/.netlify/functions/supabase-proxy
SUPABASE_SECRET_KEY=sb_secret_xxx
SUPABASE_PROJECT_URL=https://xxxx.supabase.co

LLM_API_URL=https://api.siliconflow.cn/v1/chat/completions
LLM_API_KEY=sk-xxx
LLM_MODEL=Qwen/Qwen2.5-7B-Instruct
```

> 旧版 anon key / service_role key 也兼容，填在 `VITE_SUPABASE_ANON_KEY` / `SUPABASE_SERVICE_ROLE_KEY` 即可。`VITE_` 前缀的变量会被打包进前端，secret key 千万不要加这个前缀。`.env` 已在 `.gitignore` 中。

### 4. 启动本地全栈

```bash
npm install
npm run dev:local
```

它会先构建，再用 Node 托管 `dist` 并调用 `netlify/functions/*`，访问 `http://127.0.0.1:8888`。已有 `dist` 时可以用 `npm run start:local` 跳过构建。

只调前端样式时也可以 `npm run dev`，但接口不可用。

## Supabase 反向代理

AWS 服务器上运行 `supabase-proxy.js`，前端与后端都不再直连 `*.supabase.co`：

```text
浏览器 / 本地后端
      │  https://3.105.101.123.nip.io/.netlify/functions/supabase-proxy/auth/v1/...
      ▼
AWS 上的 Node 服务（supabase-proxy.js）
      │  转发到 https://cihlexedacjmujfyipxs.supabase.co/auth/v1/...
      ▼
Supabase
```

- 前端 `VITE_SUPABASE_URL` 与后端 `SUPABASE_URL` 都填代理地址。
- 代理函数读取 `SUPABASE_PROJECT_URL` 作为真实项目地址。
- 只放行 `/auth/v1/*` 与 `/rest/v1/*`，透传 `apikey` 与 `Authorization`，secret key 不会出现在浏览器。
- 切换代理后旧登录态会失效（storage key 变化），重新登录一次即可。
- 网络没有限制时也可以把两个 URL 改回 `https://xxxx.supabase.co` 并删除 `SUPABASE_PROJECT_URL` 走直连。

## 部署到 AWS EC2

当前 AWS 实例运行方式：

- 系统：Ubuntu 24.04，t3.small
- 代码目录：`/opt/donglemei`
- 运行时：Node 22（supabase-js 需要原生 WebSocket）
- 进程管理：systemd 服务 `donglemei`
- HTTPS：Caddy 反向代理 + Let's Encrypt 自动证书
- 临时域名：`3.105.101.123.nip.io`（解析到弹性 IP）

日常更新：

```bash
ssh -i D:\Edge\donglemei_new ubuntu@3.105.101.123
cd /opt/donglemei
git pull
npm ci
npm run build
sudo systemctl restart donglemei
```

关键文件：

- systemd：`/etc/systemd/system/donglemei.service`
- Caddy：`/etc/caddy/Caddyfile`
- 环境变量：`/opt/donglemei/.env`（权限 600）

说明：

- 前端 `VITE_SUPABASE_URL` 指向 AWS 自己的 `/.netlify/functions/supabase-proxy`；后端 `SUPABASE_URL` 直连 Supabase。
- 安全组需要同时保留 `MyWebServerGroup`（22/80/443）；确认 SSH 正常后可以移除 `launch-wizard-1`。
- 旧 `donglemei.pem` 已失效，当前 SSH 密钥是 `D:\Edge\donglemei_new`，请备份好。
- 正式使用时建议把自有域名解析到弹性 IP，并修改 `/etc/caddy/Caddyfile` 后执行 `sudo systemctl reload caddy`。

## 手机号登录说明

为了让课程项目零成本跑通，手机号只作为账号标识：不发送、不校验短信验证码。

登录页只有一个账号输入框，输入 11 位手机号或邮箱后自动识别：手机号会映射为 `手机号@phone.dlm.app`，复用 Supabase 的邮箱密码体系；登录时做同样映射。代价是无法验证手机号真实归属。要真实验证需接入 Twilio 等 SMS Provider 并改用 Supabase 原生 phone auth。

## 安全设计

- 密码由 Supabase Auth 保管，只存 bcrypt 哈希，后端不接触、不存储密码
- 后端函数使用 `SUPABASE_SECRET_KEY`，前端只使用 publishable key
- 三张业务表开启 RLS，`weekly_rank` 视图设置 `security_invoker = true`，并 revoke 了 anon / authenticated 权限
- 昵称使用白名单正则（中英文、数字、下划线、连字符），前端不使用 `v-html`
- Caddy 配置 CSP、`X-Content-Type-Options`、`Referrer-Policy`

## 目录结构

```text
.
├── src/
│   ├── pages/            # Auth / Onboarding / Home / Checkin / Stats / Rank / Summary / Mine
│   ├── pages/admin/      # 后台页面
│   ├── assets/           # 空状态与引导插画
│   ├── utils/            # supabase.js / api.js / sport.js / ui.js
│   ├── router/
│   ├── styles/
│   ├── App.vue
│   └── main.js
├── netlify/functions/    # API Handler：db.js / checkin.js / stats.js / rank.js / summary.js / profile.js / admin.js / supabase-proxy.js
├── scripts/local-server.cjs  # Node HTTP 服务：托管 dist 并调用 Handler
├── supabase/schema.sql
├── public/               # PWA 图标、favicon、logo
├── vite.config.js
└── .env.example
```

## 注意事项

- Supabase 免费项目连续约 7 天无请求会被暂停，演示前先打开后台唤醒。
- 同一天同一类型只能有一条记录，由数据库唯一索引兜底；重复提交时前端会弹窗确认覆盖。
- 所有日期按 `Asia/Shanghai` 生成，避免凌晨打卡被算到前一天。
