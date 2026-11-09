# 官网（GitHub Pages）

线上地址：<https://enenhhh.github.io/smartschedule/>

纯静态，无构建步骤：`index.html` 内联样式和脚本，另有 `disclaimer.html`、`notice.html`、`404.html`、`favicon.svg`。

部署方式：Pages 的发布源是 **gh-pages 分支根目录**。改完这里的文件后，把整个目录的内容复制到 gh-pages 分支根目录并推送即可（`.nojekyll` 要保留）：

```bash
git worktree add ../ghp gh-pages
cp pages/* pages/.nojekyll ../ghp/
cd ../ghp && git add -A && git commit -m "site: 更新" && git push origin gh-pages
```

下载区块在浏览器里请求 GitHub 最新 Release，自动显示版本、大小、发布日期和 SHA-256；请求失败时使用页面里写死的 5.2 下载链接。发新版后不用改这个页面，但页面里的静态默认链接（`#dlBtn`）建议顺手更新。
