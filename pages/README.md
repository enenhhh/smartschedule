# 官网（GitHub Pages）

线上地址：<https://enenhhh.github.io/smartschedule/>

纯静态，无构建步骤。视觉和宣传片一致；页面里的手机不是截图，而是用代码复刻的应用界面，能点、能拖。

| 文件 | 作用 |
|---|---|
| `index.html` | 首页 |
| `assets/site.css` | 首页样式 |
| `assets/site.js` | 首页交互：把复刻界面装进手机、演示动画、可点击的配置教程、下载信息 |
| `assets/app.js`、`assets/lib.js`、`assets/app.css` | 复刻的应用界面，与宣传片代码共用（坐标系 694 × 1534，与真实截图对应） |
| `assets/fonts.css`、`assets/fonts/` | Google Sans Flex + Noto Sans SC（SIL OFL），按页面用字裁剪后的 woff2，共约 310 KB |
| `assets/og.jpg` | 分享卡片图（宣传主图缩小版） |
| `shots/` | 真实截图（webp）。`11_notification.jpg` 是 40 px 宽的重度模糊图，只用作通知栏壁纸的底色 |
| `tools/subset_fonts.py` | 重新裁剪字体 |
| `disclaimer.html`、`notice.html`、`404.html`、`favicon.svg` | 其它页面 |

## 改文字之后

字体只包含页面里用到的字。改了任何文字（包括 `site.js`、`app.js` 里的），重新裁一次字体，否则新字会显示成系统字体：

```bash
pip install fonttools brotli
python3 tools/subset_fonts.py GoogleSansFlex.ttf NotoSansSC.ttf   # 两个源字体从 Google Fonts 下载
```

## 部署

Pages 的发布源是 **gh-pages 分支根目录**。用本目录的内容整体替换过去并推送（`.nojekyll` 要保留，`tools/` 和本 README 不用发布）：

```bash
git worktree add ../ghp gh-pages
cd ../ghp && git rm -rq . && cp -r ../smartschedule/pages/. ./ && rm -rf tools README.md
git add -A && git commit -m "site: 更新" && git push origin gh-pages
```

下载区块在浏览器里请求 GitHub 最新 Release，自动显示版本、大小、发布日期和 SHA-256；请求失败时使用页面里写死的下载链接（当前为 5.3）。发新版后不用改这个页面，但静态默认链接（`#dlBtn`）建议顺手更新。
