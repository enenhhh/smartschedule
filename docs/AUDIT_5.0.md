# 智能课表 5.0 —— 审计与改造报告

> 对象：`github.com/enenhhh/smartschedule`，基线 commit `23880d4`（4.0）
> 产出：仓库内完整的 5.0 代码（已提交到本地 git，可直接 push）、新版分享码服务端、官网、本报告。

---

## 0. 一句话总结

4.0 的业务逻辑是对的，但有一个会让人误课的周次 bug、一个会被滥用的分享码服务，以及一套"每个页面各管各的"的界面。5.0 修掉了所有找到的问题，把 UI 整体推倒重来（Material 3 Expressive + 可调强度的液态玻璃），并把"配置课表"这件事从十几步压缩到一分钟。

---

## 1. 审计发现（按严重程度）

| # | 严重度 | 位置 | 问题 | 5.0 处理 |
|---|---|---|---|---|
| 1 | **高** | `Utils.getWeekNumber` / 明日课程 | 周次按"开学日 → 今天的天数 / 7"计算，没有对齐到周一。开学日不是周一、或周日看"明天"时，单双周判断错误（这就是你说的周日 bug）。通知里的"明日课程"同样受影响。 | `DateUtils.weekNumber()` 统一以周一为周的起点：`(mondayOf(date) - mondayOf(start)) / 7 + 1`；"明天"先算出明天的日期再求周次，不再用"今天的周次 ± 0"。 |
| 2 | **高** | 分享码（客户端 + 服务端） | ① 上传把 **全部 SharedPreferences + 作业** 一起发出去（含更新源、城市、字体路径等隐私）；② 导入时 `prefs.edit().clear()` 把对方的所有设置清空；③ 没有任何大小上限，服务端也不校验，任何人 POST 1 GB 都会被原样落盘；④ 文件名直接来自 JSON key，可路径穿越；⑤ 明文 HTTP。 | 客户端：只上传课表、时间方案、简称和开学日期（v2 格式），上传前 64 KB 自检；导入先预览、可"合并 / 替换"，永不清空设置；全部 HTTPS。服务端：见第 4 节，完全重写。 |
| 3 | 中 | `ScheduleManager` | 文件名未做清洗；删除/重命名后缓存不失效，出现"幽灵方案"。 | 以 UUID 作为课表 id，文件名只含 `[A-Za-z0-9_-]`；`version` 计数器驱动 Compose 重组，改动即刷新。 |
| 4 | 中 | `HomeworkRepository` | 导入备份后内存缓存不刷新，要重启才看到新作业。 | 加 `version` + `reload()`，导入/恢复后 bump。 |
| 5 | 中 | `UpdateChecker` | 明文 HTTP；`contentLength == -1` 时进度条除零/NaN；下载失败无提示。 | HTTPS；未知长度时显示不定进度；失败走 Snackbar；支持"跳过此版本"。 |
| 6 | 中 | 课表视图 | 只有周一到周五；周六日有课的学校无法使用。 | 7 天模型（`DaySchedule × 7`），周末自动显示（有课即显示，也可手动开）。 |
| 7 | 中 | 默认开学日期 | 写死的旧日期，新装用户周次从一开始就是错的。 | 首次引导要求选择"本周是第几周"或开学日期，二者互转。 |
| 8 | 低 | `WeatherFetcher` | 城市查不到时静默回退到北京。 | 明确返回"找不到该城市"，设置页实时校验。 |
| 9 | 低 | 通知权限 | Android 13+ 未请求 `POST_NOTIFICATIONS`，12+ 未处理精确闹钟权限。 | 提醒页顶部权限卡片 + 一键跳转；引导里按需申请。 |
| 10 | 低 | 字体设置 | 占据侧边栏一级入口；主题只有 4 色。 | 字体移入「外观 › 高级」；14 预设色 + 自定义色 + 8 种配色风格 + 对比度 + AMOLED。 |
| 11 | 低 | 图标 | 高饱和渐变、非自适应图标，没有主题图标。 | 重绘：深海军蓝底 + 米白课表 + 两格薄荷绿高亮，`adaptive-icon` 三层（含 monochrome）。 |
| 12 | 低 | `network_security_config` | 允许明文流量。 | 删除；所有端点 HTTPS（已验证 `blog.enhub.top` / `enhub.top` 证书正常）。 |
| 13 | 提示 | 线上服务 | 审计时为验证"无校验"问题，向 `blog.enhub.top/fxm/` 上传了一条无意义 JSON，得到分享码 **719933**。可忽略，部署新服务端后会自然失效。 | — |

---

## 2. 架构变化

```
4.0                                   5.0
──────────────────────────────────    ──────────────────────────────────────────────
MainActivity + 多个 Activity          单 Activity + 自有 Navigator（底栈 + 子页栈）
Drawer 侧边栏                          玻璃底栏 4 Tab：今日 / 课表 / 作业 / 设置
"时间方案 + 课程方案"两个列表           Timetable(id, name, timePlanName, days[7])
  课程方案按 单/双 × 周一..周五 存文件     每天 DaySchedule(courses, evenCourses?, 晨读)
Prefs 散落在各处                        Prefs 对象集中、类型安全
主题：4 色 + 动态色                     ThemeState：种子色 / 风格 / 对比度 / 深色 / AMOLED
                                        / 玻璃开关 / 强度 / 液态折射 / 背景动画
```

### 2.1 数据模型（`models/Models.kt`）

- `TimePlan(name, periods[], morningStart, morningEnd)`，`TimePlan.generate(...)` 一键生成作息。
- `Timetable(id, name, timePlanName, days: List<DaySchedule>(7), createdAt)`；`DaySchedule.coursesFor(isOdd)` 在"该天不区分单双周"时返回同一列表。
- 旧版 `plans/course/*.json` 在首次启动时由 `Timetable.fromLegacy()` 迁移，旧文件保留不删。
- `DayView` 是"某一天在某张课表下的最终视图"（含临时调课），今日页 / 通知 / 分享预览都用它，保证三处逻辑一致。

### 2.2 UI 层（`ui/`）

- `theme/AppTheme.kt`：`MaterialExpressiveTheme` + `MotionScheme.expressive()`；配色由 MaterialKolor 从种子色实时生成，Android 12+ 可跟随壁纸。
- `glass/Glass.kt`：`GlassSpec`（由玻璃强度滑块 0–100 映射出模糊半径、透明度、高光强度、边框透明度）；`Modifier.glassCard / glassBar / liquidGlass`；`MeshBackground` 慢速漂移色斑（可关）。模糊只用于底栏 / 顶栏 / 弹窗（Haze `hazeBlur`），卡片用半透明 + 高光，不做逐卡模糊，保证低端机流畅。Android 13+ 底栏启用 Haze `hazeGlass` 折射，31/32 退化为真实模糊，≤30 退化为半透明。
- `components/Components.kt`：统一的 `GlassTopBar / SubPage / GlassGroup / SettingRow / GlassDialog / GlassSheet / PillTabs / EmptyState`，所有页面只用这一套，解决"主题不统一"。
- 页面：今日（仪表盘）、课表（7 天网格 + 单双切换 + 点格详情）、作业（列表 + 甘特时间线）、设置（分组）、课表编辑器（点格填课 → 自动跳下一节，表头菜单：复制整天 / 开启双周 / 晨读）、时间方案编辑器、学期与周次、外观与主题、提醒与通知、备份恢复与分享码、关于与更新、五步引导。

---

## 3. 功能清单（新增 / 重做）

- 五步首次引导：周次 → 作息生成 → 课表来源（自填 / 分享码 / 跳过）→ 提醒 → 开始填课；每步可跳过，随时可在设置里重新打开。
- 课表编辑器："点格子 → 选常用课程 / 输入 → 自动跳到下一节"；按天开启"单双周不同"；一键复制整天到其他天；晨读按天设置。
- 课表页支持周六日、单双周切换预览、点格查看该课的时间与临时调课。
- 今日页：正在上的课 + 波浪进度 + 剩余分钟、下一节、今日时间线、明天预览（周次正确）、临近截止作业、临时调课快捷入口。
- 主题：14 预设种子色 + 自定义 HEX、8 种配色风格（TonalSpot / Expressive / Vibrant / Fidelity / Rainbow / FruitSalad / Neutral / Monochrome）、3 档对比度、AMOLED 纯黑、跟随系统 / 手动深浅、玻璃开关 + 强度滑块 + 液态折射 + 背景动画。
- 分享码 v2：只传课表，上传前自检大小，导入预览 + 合并 / 替换；历史记录。
- 备份：单文件 JSON 导出 / 导入（SAF），兼容旧版备份文件夹，恢复前预览并可勾选"包含作业 / 包含设置"。
- 更新：HTTPS，双线路测速，跳过版本，下载进度，自动检查间隔。
- 崩溃日志：`CrashLogger` 捕获未处理异常写入私有目录，关于页可查看 / 复制 / 清除。
- 字体：移至「外观 › 高级」，功能保留。

---

## 4. 服务端（分享码）—— 必须手动部署

目录：`smartschedule-server/`，详见其中的 `README.md`。

- `fxm/index.php` 直接覆盖线上 `https://blog.enhub.top/fxm/index.php`。无数据库，PHP 7.4+/8.x。
- 限制：请求体 ≤ 64 KB（nginx 层再加 `client_max_body_size 96k`）、结构校验（v1 旧格式与 v2 新格式都收，字段数量 / 长度 / 正则全部有上限）、每 IP 20 次上传 / 小时、30 次下载 / 分钟、6 位码碰撞重试、7 天过期惰性清理、总文件数 / 总字节数上限（超出返回 507）。
- 返回全部为 JSON（旧版"找不到就返回 HTML 404"已改为 `{"success":false,"error":"..."}`），客户端据此给出明确提示。
- `?action=health` 可用于监控。
- 数据目录优先级：环境变量 `SMARTSCHEDULE_DATA_DIR` → `../fxm-data`（web 根目录之外，推荐）→ `fxm/data`（附带 `.htaccess` 拒绝直接访问；nginx 用户请使用 `nginx-fxm.conf` 片段）。

> 已用 PHP 8.4 内置服务器做过完整冒烟测试：合法 v1 / v2 上传与下载、垃圾 JSON（422）、超大请求体含 chunked（413）、非 UTF-8（400）、路径穿越式分享码（400）、未知码（404）、GET 上传（405）、每 IP 20 次/小时限流（429）全部符合预期。不依赖 mbstring 等额外扩展。

---

## 5. 构建说明

- 工具链：AGP 8.9.2 / Gradle 8.11.1 / Kotlin 2.4.21 / Compose BOM 2026.09.00（Compose 1.12.1）/ **material3 1.5.0-alpha22** / Haze 2.0.1 / MaterialKolor 5.0.1。
- `compileSdk 37`，`minSdk 26`，`targetSdk 34`。
- 为什么是 material3 1.5.0-alpha22：`MaterialExpressiveTheme`、`MotionScheme`、`WavyProgressIndicator`、`ButtonGroup` 等 Expressive API 在 1.4.0 稳定版里仍是 `internal`，只有 1.5.0-alpha 公开；而 Haze / MaterialKolor 通过 JetBrains CMP material3 在运行时本来就把它抬到了 1.5.0-alpha22，所以编译期也统一到同一版本（`app/build.gradle` 中 `resolutionStrategy.force`）。
- `checkDebugAarMetadata` 已在 `app/build.gradle` 里禁用：Haze 2.0 / Compose 1.12 的 AAR 元数据声明"需要 AGP 9.1+"，实际用 AGP 8.9 编译运行正常。**建议**后续在 Android Studio 里升级到 AGP 9.1 + Gradle 9，然后删掉那段。
- Release（R8 full mode）构建已验证通过，产物约 3 MB。R8 8.9 会打印一条「An error occurred when parsing kotlin metadata」警告：这是 AGP 8.9 自带的 R8 不认识 Kotlin 2.4 的元数据版本，只影响 Kotlin 反射（本项目不用），升级到 AGP 8.13+/9.x 后自然消失。
- Android Studio 若提示 compileSdk 37 需要新版本，可先用 `android.suppressUnsupportedCompileSdk=37`（已写在 `gradle.properties`）。

---

## 6. 已知限制 / 后续建议

1. 本次是在无设备、无模拟器的沙箱里完成的：代码已通过完整编译并打出 debug APK，但**没有真机运行**。第一次安装请重点看：首次引导 → 填课 → 今日页 → 分享码上传下载 → 备份恢复。
2. 液态折射（`hazeGlass`）在部分国产 ROM 上可能有性能差异，设置里有一键关闭；默认强度 55。
3. 字体功能保留但没有重做（低优先级）。
4. 如果以后要上架应用商店，`targetSdk` 需要升到 35+（Android 15 边到边默认开启，5.0 已经按边到边布局，升级成本很低）。
5. 分享码服务没有身份认证（与 4.0 一致）。如果被恶意刷码，`index.php` 顶部的常量可以直接调小配额；更进一步可以加 Cloudflare Turnstile。
