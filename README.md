# 动了么

校园运动打卡全栈 Web App：记录运动、连续打卡、周榜排行，并用 AI 生成每周小结。

## 技术栈

- 前端：Vue 3 + Vite + Vue Router + ECharts + Lucide 图标，手写设计系统
- 后端：Spring Boot 3.5 + Java 21 + MyBatis-Plus，代码在 `backend-spring/`
- 数据库：MySQL 8（安装在 EC2 本机，仅监听 127.0.0.1）
- 登录：自建 JWT + bcrypt 密码哈希
- 部署：AWS EC2 + Caddy（自动 HTTPS）+ systemd（`donglemei-java`）
- PWA：vite-plugin-pwa

## 功能

- 单输入框自动识别手机号 / 邮箱，密码注册与登录
- 注册后引导设置昵称、体重、周目标
- 7 种运动打卡，按 MET 公式实时估算卡路里
- 连续打卡 streak、今日数据、本周进度
- 近 7 天 / 30 天 / 本月统计，柱状图 + 饼图
- 本周 Top 20 排行榜，支持“我的排名”
- 每周 AI 小结，支持缓存与重新生成
- 资料编辑、修改密码、数据导出、清空数据、退出登录
- 后台管理：概览、用户、打卡记录、排行榜

## 本地启动

本地开发需要 Node 22+ 与 Java 21。

### 1. 安装依赖

```bash
npm install
```

### 2. 准备 MySQL

在本地或服务器安装 MySQL 8，使用 root 执行：

```bash
mysql -u root -p < mysql/schema.sql
```

然后创建应用账号（密码自行替换）。应用账号只给数据读写权限，**不给建表/改表权限**：

```sql
CREATE USER 'dlm'@'127.0.0.1' IDENTIFIED BY '你的密码';
CREATE USER 'dlm'@'localhost' IDENTIFIED BY '你的密码';
GRANT SELECT, INSERT, UPDATE, DELETE ON donglemei.* TO 'dlm'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON donglemei.* TO 'dlm'@'localhost';
FLUSH PRIVILEGES;
```

结构变更一律用 root 执行 `mysql/schema.sql`，应用启动时不会跑任何 DDL。

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

### 4. 启动 Spring Boot 后端

如果数据库在云服务器，本地先开 SSH 隧道，并把 `.env` 的 `DB_PORT` 改成 `13306`：

```bash
ssh -i <你的 SSH 私钥路径> -N -L 13306:127.0.0.1:3306 <用户名>@<服务器地址>
```

```bash
cd backend-spring
mvn -DskipTests package
JWT_SECRET=你的密钥 DB_HOST=127.0.0.1 DB_PORT=13306 DB_USER=dlm DB_PASSWORD=你的数据库密码 \
  java -jar target/dongleme-backend.jar
```

默认监听 `8080`，接口路径是 `/api/*`，数据库沿用 `donglemei`。

### 5. 启动前端

```bash
npm run dev
```

访问 `http://127.0.0.1:5173`，Vite 会把 `/api` 代理到 `127.0.0.1:8080`。

也可以直接运行 `start-local.bat`：它会自动拉起 SSH 隧道、后端 jar 和前端 dev server。

## Spring Boot 后端

`backend-spring/` 完整实现认证、资料、打卡、统计、排行、周小结和后台管理，线上正在使用。

迁移前的 Node 版后端已从主干删除，需要查阅或回滚时从 git tag 取出：

```bash
git show archive/node-backend:server/checkin.js
git checkout archive/node-backend -- server/
```

## 数据库结构

[`mysql/schema.sql`](./mysql/schema.sql) 包含：

- `users`：账号、bcrypt 密码、昵称、体重、周目标、管理员标记、连续打卡
- `records`：打卡记录，`unique(user_id, date, type)` 防重复
- `summaries`：每周 AI 小结缓存与重新生成次数
- `custom_sports`：用户自定义运动及 MET 值
- `activity_routes`：GPS 运动轨迹、降采样缩略图与统计

所有日期按 `Asia/Shanghai` 生成，数据库只存 `DATE` / `DATETIME`。

`mysql/schema.sql` 是**唯一的结构来源**：建表、加字段、加索引都写进这个文件，用 root 账号执行。
应用启动时不执行任何 DDL，数据库账号只有 `SELECT/INSERT/UPDATE/DELETE` 权限。

## 账号与密码

- 从 Supabase 迁移的账号已重置密码，请向管理员获取
- 登录后在「我的 → 账号安全 → 修改密码」里改成自己的密码
- 新注册用户注册后进入 `/onboarding` 设置昵称、体重、周目标
- 后台管理员使用「管理员账号 + 密码」登录 `/admin/login`

## 部署到 AWS EC2

当前实例：

- 系统：Ubuntu 24.04，t3.small
- 代码目录：`/opt/donglemei`
- 后端：Spring Boot 3.5 + Java 21，jar 位于 `/opt/donglemei/backend-spring/app.jar`
- 进程管理：systemd 服务 `donglemei-java`
- 前端：`/opt/donglemei/dist`，由 Caddy 直接托管
- HTTPS：Caddy + Let's Encrypt

Caddy 路由：

```text
/api/*  -> 127.0.0.1:8080（Spring Boot）
其余路径 -> /opt/donglemei/dist（前端静态文件）
```

日常更新：

1. 本地构建并上传 jar：

```powershell
cd backend-spring
mvn -DskipTests package
scp -i <你的 SSH 私钥路径> backend-spring\target\dongleme-backend.jar <用户名>@<服务器地址>:/opt/donglemei/backend-spring/app.jar
```

2. 服务器更新前端并重启后端：

```bash
ssh -i <你的 SSH 私钥路径> <用户名>@<服务器地址>
cd /opt/donglemei
git pull
npm ci
npm run build
sudo systemctl restart donglemei-java
```

如果修改了 `mysql/schema.sql`，需要手动在服务器执行：

```bash
mysql -u root -p < /opt/donglemei/mysql/schema.sql
```

关键文件：

- Spring Boot systemd：`/etc/systemd/system/donglemei-java.service`
- Caddy：`/etc/caddy/Caddyfile`
- 环境变量：`/opt/donglemei/.env`（权限 600）

回滚到上一版 jar：服务器上保留上一版 jar 副本，替换后重启即可。

```bash
sudo systemctl stop donglemei-java
sudo cp /opt/donglemei/backend-spring/app.jar.bak /opt/donglemei/backend-spring/app.jar
sudo systemctl start donglemei-java
```

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

- 密码使用 bcrypt 哈希，后端不保存明文，注册与改密都要求至少 6 位
- 登录失败限流（内存计数）：账号维度 15 分钟 5 次、IP 维度 15 分钟 30 次，超限返回 429
- 账号不存在时也执行一次 bcrypt 比对，避免通过响应时间枚举已注册账号
- 登录签发 7 天 JWT，后端每次请求校验并读取当前用户
- 所有写操作都从 JWT 取用户 id，不信任前端传入的 userId
- 昵称白名单正则（中英文、数字、下划线、连字符，1–12 字），前端不使用 `v-html`
- MySQL 只监听 127.0.0.1，仅 EC2 本机可访问；应用账号只有 `SELECT/INSERT/UPDATE/DELETE`，没有 DDL 权限
- Caddy 配置 CSP、`X-Content-Type-Options`、`Referrer-Policy`
- 生产环境同源部署，后端默认不发送任何 CORS 放行头；只有显式配置 `CORS_ALLOWED_ORIGINS` 才放行指定来源

## 前端结构约定

分层原则是「页面编排、组件展示、composable 承载逻辑」：

| 层 | 职责 | 例子 |
| --- | --- | --- |
| `pages/` | 拉数据、状态编排、布局 | `Home.vue`、`Track.vue` |
| `components/` | 只接收 props 并渲染，不发请求 | `HomeSportGrid`、`WeatherInline`、`WeatherCanvas` |
| `composables/` | 可复用逻辑，不碰 DOM 结构 | `useTracker`（GPS/计时/草稿/WakeLock）、`useWeather`、`useRouteHistory`、`useLocateProbe` |
| `utils/` | 纯函数与常量表 | `weatherTheme.js`、`routeFormat.js`、`geo.js` |

天气模块的边界：后端 `WeatherService` 决定天气类型与昼夜，`utils/weatherTheme.js` 是唯一的「类型 → 视觉配置」映射，
`weatherEngine.js`（Canvas 动画）与 `WeatherScene.vue`（SVG 兜底）都只负责按配置绘制，不再各自判断天气类型。

后台样式按区块拆在 `styles/admin/` 下，`admin.css` 只做 `@import` 入口；新增样式请加到对应区块文件，不要另开零散规则。

## 测试

后端单元测试覆盖风险最高的三块：打卡去重、连续天数 streak、时区与周边界。

```bash
npm run test:backend
# 等价于 cd backend-spring && mvn test
```

| 测试类 | 覆盖点 |
| --- | --- |
| `CheckinServiceTest` | 时长/体重校验、MET 热量公式、性别系数、同日重复返回 409、覆盖写入、streak 递增与归零 |
| `ShanghaiTimeTest` | `Asia/Shanghai` 时区、凌晨跨天、周边界（含跨月跨年）、streak 有效期 |
| `LoginThrottleTest` | 账号/IP 双维度限流、跨 IP 锁同一账号、成功登录清零、窗口过期重置、账号归一化 |

## 目录结构

```text
.
├── src/
│   ├── pages/            # 页面：只做数据编排与布局
│   ├── pages/admin/      # 后台页面
│   ├── components/       # 可复用展示组件（天气、地图、宫格、列表等）
│   ├── composables/      # 逻辑复用：useTracker / useWeather / useRouteHistory / useLocateProbe
│   ├── utils/            # auth / api / sport / geo / weatherTheme / routeFormat
│   ├── router/
│   ├── styles/           # global.css + admin.css（入口）+ admin/*.css（分区块）
│   ├── assets/           # 空状态与引导插画
│   ├── App.vue
│   └── main.js
├── backend-spring/       # Spring Boot 3.5 后端（生产）
│   ├── src/main/java/    # common / config / controller / dto / entity / mapper / security / service
│   ├── src/test/java/    # 单元测试
│   ├── src/main/resources/application.yml
│   └── pom.xml
├── scripts/
│   └── migrate-supabase-to-mysql.mjs  # 一次性迁移脚本，已执行完毕
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
