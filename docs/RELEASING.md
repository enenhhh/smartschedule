# 发布流程

应用默认从 **GitHub Release** 检查更新和下载安装包，自建服务器作为备选（设置 → 关于与更新 → 更新源）。
GitHub 失败时，应用会自动依次尝试自建服务器。

## 约定

| 项 | 规则 |
|---|---|
| tag | `v<版本号>`，如 `v5.2`，版本号只含数字和点，要和 `versionName` 一致 |
| 附件 `smartschedule-<版本号>.apk` | 用正式签名（与 4.0 同一把密钥）打出的 release 包 |
| 附件 `update.txt` | 第 1 行版本号；可选一行 `sha256: <64 位十六进制>`；其余为更新说明 |

`update.txt` 里写了 `sha256:` 时，应用下载完会校验，不一致就丢弃安装包。

应用访问的地址：

- 检查：`https://github.com/enenhhh/smartschedule/releases/latest/download/update.txt`
- 下载：`https://github.com/enenhhh/smartschedule/releases/download/v<版本号>/smartschedule-<版本号>.apk`

## 步骤

1. 改 `app/build.gradle` 的 `versionCode` / `versionName`。
2. 用 `keystore.properties` 里的正式签名打包：`./gradlew assembleRelease`。
3. 把更新说明写进一个文件（如 `notes.md`），然后生成附件：

   ```bash
   tools/make_release_assets.sh app/build/outputs/apk/release/app-release.apk 5.2 notes.md
   ```

4. 创建 Release（脚本最后会打印这条命令）：

   ```bash
   gh release create v5.2 dist/smartschedule-5.2.apk dist/update.txt --title "智能课表 5.2" --notes-file notes.md
   ```

5. 如果还想同步到自建服务器：把 `update.txt` 和 APK（`<版本号>.apk`）传到 `/files/smartschedule/`。

## 注意

- 必须保证 `releases/latest` 指向最新的正式版，不要把预发布（pre-release）标成 latest。
- `.jks` 与 `keystore.properties` 不进仓库（已在 `.gitignore`）。
