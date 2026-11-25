# 动了么 · 官网

纯静态站点，没有构建步骤，`index.html` 直接打开就能跑。

```
site/
├── index.html      # 页面结构
├── styles.css      # 全部样式
├── app.js          # 滚动入场、手机导航状态、APK 降级
├── privacy.html    # 隐私政策
├── terms.html      # 使用条款
├── assets/         # 海报、真实产品截图与 logo
├── tests/          # 无依赖静态站点检查
└── download/       # 放 APK 的位置
```

## 本地预览

```bash
cd site
python -m http.server 4321
# 打开 http://127.0.0.1:4321
```

直接双击 `index.html` 也能看。页面默认展示全部内容，JavaScript 只负责渐进启用入场动画；APK 存在性检查（HEAD 请求）在 `file://` 下会保留原始下载链接。

## 检查

在仓库根目录执行：

```bash
npm run test:site
```

检查手机锚点、首屏动画、无 JavaScript 兜底、APK 缺失与网络异常降级、协议链接和产品素材。

## 放 APK

把打包好的安装包命名为 `dongleme.apk`，放到 `site/download/`：

```
site/download/dongleme.apk
```

按钮指向 `download/dongleme.apk`。**如果文件不存在**，`app.js` 会自动把按钮降级成「Android 版准备中」并跳转到网页版，不会出现点了没反应的死链。

APK 是二进制产物，已在 `.gitignore` 里排除，部署时单独上传：

```bash
scp dongleme.apk ubuntu@<服务器>:/opt/donglemei/site/download/
```

## 需要改的地方

应用域名写在 `index.html` 里（导航 CTA、首屏「在线体验」、下载区的网页版按钮和页脚入口）。换域名时在 `index.html` 与 `app.js` 中全局替换 `https://app.dongleme.xyz`；`app.js` 中的地址用于 APK 缺失或网络异常时跳转网页版。

`privacy.html` 和 `terms.html` 是独立静态页面，部署 `site/` 时会一起上传。修改条款后，请同步核对应用内 `Auth.vue` 与 `Settings.vue` 的隐私说明，避免文案和实际数据处理不一致。

## 部署（已上线）

当前线上布局：

```
dongleme.xyz / www.dongleme.xyz  ->  官网（/opt/donglemei/site）
app.dongleme.xyz                 ->  App（/opt/donglemei/dist）
```

`app.dongleme.xyz` 需要在阿里云 DNS 添加 A 记录指向 `3.105.101.123`，Caddy 会自动签发证书。

Caddy 里是两个站点块：官网（主域名 + www）和 App（`app` 子域名 + `nip.io` 备用）；两个站点都保留 `/api/*` 反代，安卓客户端继续使用主域名的接口。

更新官网：

```bash
scp -r -i ~/.ssh/<key> site ubuntu@<服务器>:/opt/donglemei/
```

不需要 reload caddy —— 静态文件直接覆盖即可生效（`index.html` 设了 `no-cache`，`assets/` 缓存 30 天，文件名不变的话改图要改引用或加版本号）。

改 Caddy 配置时：

```bash
sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
sudo systemctl reload caddy
```

## 素材来源

`assets/` 里的图是用 `sharp` 从仓库根目录的三张海报缩到 760px 宽、质量 82 生成的，
单张 90–150KB。原始 PNG 都在 1–2MB，不要直接引用。
