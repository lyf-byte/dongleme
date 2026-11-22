# 动了么 · 官网

纯静态站点，没有构建步骤，`index.html` 直接打开就能跑。

```
site/
├── index.html      # 页面结构
├── styles.css      # 全部样式
├── app.js          # 滚动入场、导航状态、APK 降级
├── assets/         # 压缩后的海报与 logo
└── download/       # 放 APK 的位置
```

## 本地预览

```bash
cd site
python -m http.server 4321
# 打开 http://127.0.0.1:4321
```

直接双击 `index.html` 也能看，只是 APK 存在性检查（HEAD 请求）在 `file://` 下会被跳过。

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

域名写在 `index.html` 里，共 4 处 `https://dongleme.xyz`（导航 CTA、首屏「在线体验」、下载区的网页版按钮、页脚）。换域名时全局替换即可。

`app.js` 里也有一处，是 APK 缺失时的兜底跳转地址。

## 部署

推荐**独立子域名**，和 App 互不干扰：

```
www.dongleme.xyz  ->  官网（site/）
dongleme.xyz      ->  App（dist/）
```

Caddyfile 追加一个站点块：

```caddy
www.dongleme.xyz {
    root * /opt/donglemei/site
    encode zstd gzip
    try_files {path} {path}/ /index.html
    file_server
    header {
        X-Content-Type-Options nosniff
        Referrer-Policy strict-origin-when-cross-origin
    }
}
```

上传与重载：

```bash
rsync -av --delete site/ ubuntu@<服务器>:/opt/donglemei/site/
sudo systemctl reload caddy
```

DNS 那边给 `www` 加一条 A 记录指向同一台机器即可，证书会自动签发。

> 想让官网占据根域名、App 挪到 `/app` 也可以，但需要改 `vite.config.js` 的 `base` 和
> `createWebHistory('/app')`，还要处理 PWA 的 scope，改动面比较大，不如子域名干净。

## 素材来源

`assets/` 里的图是用 `sharp` 从仓库根目录的三张海报缩到 760px 宽、质量 82 生成的，
单张 90–150KB。原始 PNG 都在 1–2MB，不要直接引用。
