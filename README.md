# 动了么

校园运动打卡全栈 Web App：记录运动、连续打卡、周榜排行，并用 AI 生成每周小结。

## 线上地址

- 用户端：<https://3.105.101.123.nip.io>
- 后台入口：<https://3.105.101.123.nip.io/admin/login>
- 实例：Ubuntu 24.04 / t3.small / 弹性 IP `3.105.101.123`
- SSH：`ssh -i D:\Edge\donglemei_new ubuntu@3.105.101.123`

## 技术栈

- 前端：Vue 3 + Vite + Vue Router + ECharts + Lucide 图标，手写设计系统
- 后端：Node.js HTTP 服务，复用 `server/` 下的 Handler
- 数据库：MySQL 8（安装在 EC2 本机，仅监听 127.0.0.1）
- 登录：自建 JWT + bcrypt 密码哈希
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
- 资料编辑、修改密码、数据导出、清空数据、退出登录
- 后台管理：概览、用户、打卡记录、排行榜

## 本地启动

需要 Node 22+（后端使用 `mysql2` 与 JWT）。

### 1. 安装依赖

```bash
npm install
```

### 2. 准备 MySQL

在本地或服务器安装 MySQL 8，使用 root 执行：

```bash
mysql -u root -p < mysql/schema.sql
```

然后创建应用账号（密码自行替换）：

```sql
CREATE USER 'dlm'@'127.0.0.1' IDENTIFIED BY '你的密码';
CREATE USER 'dlm'@'localhost' IDENTIFIED BY '你的密码';
GRANT ALL PRIVILEGES ON donglemei.* TO 'dlm'@'127.0.0.1';
GRANT ALL PRIVILEGES ON donglemei.* TO 'dlm'@'localhost';
FLUSH PRIVILEGES;
```

### 3. 配置环境变量

```bash
copy .env.example .env
```

填写：

```env
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=donglemei
DB_USER=dlm
DB_PASSWORD=你的数据库密码

# 生成方式：node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
JWT_SECRET=随机 64 位十六进制

LLM_API_URL=https://api.siliconflow.cn/v1/chat/completions
LLM_API_KEY=sk-xxx
LLM_MODEL=Qwen/Qwen2.5-7B-Instruct
```

`.env` 已在 `.gitignore` 中。

### 4. 启动本地全栈

```bash
npm run dev:local
```

访问 `http://127.0.0.1:8888`。已有 `dist` 时可以用 `npm run start:local` 跳过构建。

如果数据库在 AWS，本地先开 SSH 隧道，并把 `.env` 的 `DB_PORT` 改成 `13306`：

```bash
ssh -i D:\Edge\donglemei_new -N -L 13306:127.0.0.1:3306 ubuntu@3.105.101.123
```

## 数据库结构

[`mysql/schema.sql`](./mysql/schema.sql) 包含：

- `users`：账号、bcrypt 密码、昵称、体重、周目标、管理员标记、连续打卡
- `records`：打卡记录，`unique(user_id, date, type)` 防重复
- `summaries`：每周 AI 小结缓存与重新生成次数

所有日期按 `Asia/Shanghai` 在 Node 侧生成，数据库只存 `DATE` / `DATETIME`。

## 账号与密码

- 从 Supabase 迁移的账号已重置密码，请向管理员获取
- 登录后在「我的 → 账号安全 → 修改密码」里改成自己的密码
- 新注册用户注册后进入 `/onboarding` 设置昵称、体重、周目标
- 后台管理员使用「管理员账号 + 密码」登录 `/admin/login`

## 部署到 AWS EC2

当前实例：

- 系统：Ubuntu 24.04，t3.small
- 代码目录：`/opt/donglemei`
- 运行时：Node 22
- 进程管理：systemd 服务 `donglemei`
- HTTPS：Caddy + Let's Encrypt
- 临时域名：`3.105.101.123.nip.io`

日常更新：

```bash
ssh -i D:\Edge\donglemei_new ubuntu@3.105.101.123
cd /opt/donglemei
git pull
npm ci
npm run build
sudo systemctl restart donglemei
```

如果修改了 `mysql/schema.sql`，需要手动在服务器执行：

```bash
mysql -u root -p < /opt/donglemei/mysql/schema.sql
```

关键文件：

- systemd：`/etc/systemd/system/donglemei.service`
- Caddy：`/etc/caddy/Caddyfile`
- 环境变量：`/opt/donglemei/.env`（权限 600）

## 数据备份

建议每天备份 MySQL：

```bash
mysqldump -u dlm -p donglemei > /opt/donglemei/backup-$(date +%F).sql
```

可以加到 crontab，并定期把备份下载到本地。

迁移前的 Supabase 数据备份保留在：

```text
/opt/donglemei/backup-supabase-2026-11-19.json
```

## 安全设计

- 密码使用 bcrypt 哈希，后端不保存明文
- 登录签发 7 天 JWT，后端每次请求校验并读取当前用户
- 所有写操作都从 JWT 取用户 id，不信任前端传入的 userId
- 昵称白名单正则（中英文、数字、下划线、连字符，1–12 字），前端不使用 `v-html`
- MySQL 只监听 127.0.0.1，仅 EC2 本机可访问
- Caddy 配置 CSP、`X-Content-Type-Options`、`Referrer-Policy`

## 目录结构

```text
.
├── src/
│   ├── pages/            # Auth / Onboarding / Home / Checkin / Stats / Rank / Summary / Mine
│   ├── pages/admin/      # 后台页面
│   ├── assets/           # 空状态与引导插画
│   ├── utils/            # auth.js / api.js / sport.js / ui.js
│   ├── router/
│   ├── styles/
│   ├── App.vue
│   └── main.js
├── server/               # API Handler：db.js / auth.js / checkin.js / stats.js / rank.js / summary.js / profile.js / admin.js
├── scripts/
│   ├── local-server.cjs  # Node HTTP 服务：托管 dist 并调用 Handler
│   └── migrate-supabase-to-mysql.mjs
├── mysql/schema.sql
├── public/               # PWA 图标、favicon、logo
├── vite.config.js
└── .env.example
```

## 注意事项

- 同一天同一类型只能有一条记录，由 MySQL 唯一索引兜底；重复提交时前端会弹窗确认覆盖。
- 所有日期按 `Asia/Shanghai` 生成，避免凌晨打卡被算到前一天。
- 服务器上的 MySQL 不要对公网开放；本地开发用 SSH 隧道。
- Supabase 已停用，仅保留迁移前的数据备份用于回滚。
