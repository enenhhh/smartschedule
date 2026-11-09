# 智能课表 Smart Schedule

一个面向中学 / 大学的课表应用，专门处理**单双周轮换**的课表结构，并在首页实时显示当前上到第几节、还剩多久下课。

- 纯 Kotlin + Jetpack Compose + Material 3
- 无账号、无后台、数据全部存在本机
- Release 包 **2.5 MB**

---

## 功能

**课表**
- 时间方案与课程方案分离：一套上下课时间可以被多套课程表复用
- 课程方案按「单周 / 双周 × 星期几」绑定生效，自动轮换
- 支持早读
- 课程简写，让首页和课表格子里能放下更多字
- 长按首页课程名可**临时换课**，只对当天生效

**首页**
- 实时状态：早读中 / 上课中 / 课间 / 休息中 / 等待上课 / 今天的课都结束了
- 下课倒计时与进度条
- 明日课程预览、待办作业摘要

**作业**
- 按周的甘特图视图，一眼看出这周哪几天堆了作业
- 按科目自动配色
- 批量添加：一次选多个科目，每科一栏填内容
- 快捷截止日期（明天 / 后天 / 下周一）

**提醒**
- 明日课表定时推送
- 明日天气推送（数据来自 [Open-Meteo](https://open-meteo.com/)，免费无需 key）
- 通知栏常驻今日 / 明日课表
- 可选前台保活服务

**数据**
- 导出 / 导入配置到任意文件夹
- **分享码**：生成 6 位数字码，同学输入即可完整导入你的课表
- 应用内检查更新：默认 GitHub Release，可切换自建服务器，多源自动回退与测速

**外观**
- 四套主题色 + Android 12+ 动态取色
- 深色模式（跟随系统 / 强制深色 / 强制浅色），改完立即生效
- 可加载本机 ttf / otf 自定义字体

---

## 技术栈

| | |
|---|---|
| 语言 | Kotlin 2.3 |
| UI | Jetpack Compose，Material 3（Compose BOM 2026.01） |
| 架构 | 单 Activity 群 + Compose 屏幕，无 Fragment / 无 XML 布局 |
| 存储 | SharedPreferences + JSON 文件，无数据库 |
| 网络 | `HttpURLConnection`，无第三方网络库 |
| 最低版本 | Android 8.0（API 26） |

依赖刻意保持精简：除 Compose 与少量 AndroidX 外没有第三方 UI 库。

---

## 构建

### 环境

- JDK 17+（用 Android Studio 自带的 JBR 即可）
- Android SDK Platform 37、Build-Tools 35+
- Gradle 8.11.1（由 wrapper 自动下载）

```bash
git clone <repo-url>
cd smartschedule
./gradlew assembleDebug
```

### 打正式包

正式签名配置从项目根目录的 `keystore.properties` 读取，**该文件不进版本库**。没有它时 `assembleRelease` 会产出未签名包，构建不会失败。

1. 生成密钥库：

   ```bash
   keytool -genkeypair -v -keystore smartschedule-release.jks \
           -alias smartschedule -keyalg RSA -keysize 2048 -validity 10000
   ```

2. 复制 `keystore.properties.example` 为 `keystore.properties` 并填入口令

3. 打包：

   ```bash
   ./gradlew assembleRelease
   ```

Release 构建开启了 R8 与资源收缩，产物约 2.5 MB（Debug 包约 20 MB）。

> ⚠️ `.jks` 文件丢失后将无法再发布可覆盖安装的更新，所有用户都要卸载重装。请务必多处备份。

### 两个已知的构建坑

**1. SDK Platform 37 的目录名**

Android 16 之后 SDK 引入了次版本号，`sdkmanager` 装出来的目录叫 `android-37.0`，而 AGP 8.9 只认 `android-37`，会报：

```
Failed to find Platform SDK with path: platforms;android-37
```

解决办法二选一：
- 把 `compileSdkVersion` 降到 `36`（当前依赖的实际下限就是 36，降下去无任何损失）
- 或在 SDK 的 `platforms/` 下建一个名为 `android-37` 的副本，并把其中 `source.properties` 的 `AndroidVersion.ApiLevel` 和 `package.xml` 的 `path` 改成不带小数的形式

**2. 依赖解析卡死**

仓库列表里阿里云镜像排在最前，一旦镜像不可达，**Gradle 默认没有 HTTP 超时**，依赖解析会无限期挂起（表现为构建卡在 `checkDebugAarMetadata` 一动不动，CPU 占用接近 0）。

`gradle.properties` 里已加了超时设置来兜底：

```properties
systemProp.org.gradle.internal.http.connectionTimeout=15000
systemProp.org.gradle.internal.http.socketTimeout=60000
```

如果仍然很慢，可临时用 `--init-script` 把镜像摘掉，或直接改 `app/build.gradle` 里的 `repositories` 顺序。

---

## 项目结构

```
app/src/main/java/com/enenhhh/smartschedule/
├── MainActivity.kt              唯一的 Activity，页面由 Compose 内部导航
├── App.kt                       Application：主题 / 字体 / 全局时钟初始化
├── data/                        ScheduleManager（课表读写 + 缓存）/ HomeworkRepository / TempSwaps（临时调课）
├── models/Models.kt             TimePlan / Timetable / DaySchedule / DayView
├── legal/                       免责条款、用户须知与同意记录
├── weather/                     天气与定位城市
├── ui/
│   ├── app/                     SmartScheduleApp（根布局）/ GlassBottomNav
│   ├── nav/                     自带的 Navigator
│   ├── today / timetable / homework / timeplan / onboarding / settings / legal
│   ├── glass/Glass.kt           液态玻璃 / 毛玻璃
│   ├── components/              通用组件
│   └── theme/                   配色 / 形状 / 字体
├── notifications/  services/  receivers/
└── util/                        Prefs / AppClock（全局时钟）/ UpdateChecker / BackupHelper / ShareCodeHelper / CrashLogger ...
server/   分享码服务（PHP）      site/   官网      legal/   条款文本      docs/   发布说明与审计报告
```

发布流程见 [docs/RELEASING.md](docs/RELEASING.md)。

---

## 版本历史

### 5.1

见 [docs/RELEASE_NOTES_5.1.md](docs/RELEASE_NOTES_5.1.md)。5.0 起 UI 与数据模型整体重做，详见 [docs/AUDIT_5.0.md](docs/AUDIT_5.0.md)。合并进主干后的补充：

- **更新源**：默认从 GitHub Release 检查并下载更新，可在「关于与更新」手动切到自建服务器；GitHub 失败时自动回退。`update.txt` 可带 `sha256:` 行，下载后校验安装包。
- **毛玻璃**：未开启液态折射或系统低于 Android 13 时使用真实背景模糊（Android 12+）；降低了顶栏 / 底栏的遮罩不透明度，加大模糊半径。Android 11 及以下无法实时模糊，使用半透明遮罩。对话框与底部面板改用独立的半透明面板（它们是独立窗口，取不到背后页面）。
- **跨天刷新**：新增全局时钟 `AppClock`，回到前台、日期 / 时间 / 时区变化、亮屏解锁及界面心跳都会刷新「今天」。此前进程被保活数天后，课表页会停在打开 App 的那一天。

### 4.0

**UI 全面重做**
- 从 Miuix 迁移到 Material 3，删除全部 XML 布局、Fragment 与 RecyclerView Adapter
- 重做配色（标准 M3 中性色阶，四套主题各自独立的 secondary / tertiary）、圆角与中文排版
- 重做首启引导：新增「开学日期」步骤并实时显示当前是第几周、数据来源可见选中态、返回键回退

**体积**
- Release 开启 R8 + 资源收缩，收紧 ProGuard 规则
- 移除未使用的依赖：FluentUI、Lottie、ShadowLayout、ImmersionBar、RecyclerView、Navigation、ConstraintLayout 等
- 应用图标由 1254×1254 PNG 缩至 384×384（766 KB → 119 KB）

**修复**
- 更新流程曾全局关闭 TLS 证书与主机名校验，而该链路最终会下载并安装 APK，已改回系统默认校验
- `usesCleartextTraffic` 由全局放开收窄为仅限更新源域名
- targetSdk 34 缺少 `FOREGROUND_SERVICE_DATA_SYNC`，会导致保活服务在 Android 14 崩溃
- 首页原本每秒重新读取全部方案与作业文件，改为静态数据只读一次、仅倒计时逐秒刷新
- 课程简写与方案文件加进程级缓存（此前每次调用都读盘并解析 JSON）
- 补上「开学日期」「提醒时间」「天气城市」等此前有实现但无入口的设置项
- 深色模式与主题色改完立即生效，不再需要重启
- 通知补上点击跳转
- 作业科目配色改为按名称哈希，不再随插入顺序变化

### 3.0
方案化改版：时间方案与课程方案分离，支持单双周绑定。

### 2.x
初版，固定课表结构。

---

## 反馈

- GitHub Issues
- QQ 6944341
- 邮箱 enenhhh@enhub.top
