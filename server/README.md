# 智能课表分享码服务 2.1（App 5.1）

PHP 7.4+，无需数据库。与旧客户端保留相同入口：

- `POST /fxm/?action=upload` — JSON 载荷，返回 6 位分享码。
- `GET /fxm/?action=download&code=123456` — 返回原载荷；旧 `/fxm/data/*.json` 仍可读取，按原过期时间判断。
- `GET /fxm/?action=health` — 返回服务版本、体积限制和兼容状态，不披露用户数据或存储路径。

## 部署

把 `fxm/index.php` 放到网站的 `/fxm/index.php`。不要覆盖博客首页，不要删除 `fxm/data/`。
`fxm/data/.htaccess` 是 Apache 的禁止直接访问配置；nginx 用户请使用 `nginx-fxm.conf`。

存储优先使用 `SMARTSCHEDULE_DATA_DIR` 环境变量指定的目录，否则尝试 **网站根目录之外** 的 `smartschedule-private/`。
受虚拟主机 `open_basedir` 或权限限制时，回退到 `/fxm/private/`。新分享、限流记录与分配锁使用带 PHP 退出保护的 `.php` 文件，直接请求不返回记录正文；同时附加 `.htaccess` 拒绝访问。PHP 必须在服务器正确配置为执行而非下载源码。

**旧存储注意事项**：为保留回滚与旧码兼容，本次不删除或改写旧 JSON。请在 Web 服务器层禁止直接访问 `/fxm/data/`。Apache `.htaccess` 可随 FTP 上传；nginx 的 server 配置不能仅靠 FTP 保证生效，需要网站管理面板配置。新存储有 PHP 保护，不依赖 nginx 的目录列表设置。

## 限制与验证

- 64 KiB 上传上限：先检查 Content-Length，再流式限量读取。
- v2 仅接受 `v=2` 的课表结构；旧 v1 `prefs/plans/homework` 保留兼容。
- 校验实际日期、24 小时时间、字段长度、数量、UTF-8 与路径形状。
- 上传每来源 IP 每小时 20 次，下载每分钟 30 次；不信任客户端自行填写的转发地址头。
- 7 天有效；过期数据惰性清理；最多 20,000 个分享 / 512 MiB。
- 文件锁串行执行容量检查和分配；原子创建，碰撞时重试，**不删除另一个并发请求创建的代码**。
- 不存储位置、不提供任意 URL 代理、不在错误响应中暴露路径。
- 未授权的六位码仍不是私密存储；避免分享敏感数据。反代部署时在服务器配置真实来源地址，否则所有用户可能共用限额。

本地测试（不会向线上发送压力请求）：

```sh
SMARTSCHEDULE_DATA_DIR="$PWD/.test-data" php -S 127.0.0.1:8081 -t .
# 仓库 tools/test_server.py 的默认路径针对交付工作区；按需调整 PRIVATE/BASE。
python ../tools/test_server.py
```

本次已执行 39 项本地检查：合法 v1/v2 上传下载、旧码读取、过期码、异常 JSON、超大请求、路径输入、非法时间和日期、限流与存储保护。

## 城市定位为什么不经过这里

App 仅在用户点击时用安卓系统网络定位获取大致位置，再从**设备端**调用 BigDataCloud 的 Free Client Side Reverse Geocode API。
此接口要求由设备直接调用、使用当前真实位置，不可用服务器转发、批量查询或查询预存位置。
应用不在后台调用它；之后的天气请求使用本机保存的城市与降低精度后的坐标，直接请求 Open-Meteo。

服务说明与使用政策：https://www.bigdatacloud.com/free-api/free-reverse-geocode-to-city-api

若服务不可用，App 明确报错并保留手动选城市。请勿用本服务器批量测试该客户端专用接口。
