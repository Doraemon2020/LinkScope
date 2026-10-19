# LinkScope · 独立连通性测试 App（Android / Android TV）

LinkScope 将 Bettbox 的连通性／媒体解锁检测思路独立化，以四卡片 UI 为默认首页（ViuTV、myTV SUPER、Gemini、OpenAI），支持电视遥控器及 Android 触控设备。

> **交付范围**：这是完整 Android Studio *源码工程*，不是已经编译、安装验证的 APK。本工作环境没有 Android SDK/Gradle/模拟器，无法实际编译或在电视盒子上验证。CI 工作流已提供。首次编译请在联网的 Android Studio 或 GitHub Actions 中进行。

## 功能

- 首屏同时进行四项真实 HTTPS 检测，4 路并发。
- 点击卡片单独重测，长按卡片查看请求 URL、HTTP 响应码、耗时、出口 IP（如服务提供）及判断原因。
- 顶部 `⟳` 全部刷新、`☷` 管理测试项目、`⚙` 选择系统网络或 HTTP 代理。
- 更多项目：HOY TV、RTHK、Netflix、YouTube、Cloudflare、GitHub。可勾选展示项目；配置本机持久化。
- 支持 Android 8.0 以上，包含 Android TV 启动入口；Android TV 遥控器焦点高亮。
- 使用标准系统 TLS 验证，禁止 HTTP 明文请求，不需要 VPN 权限。
- 无后台测试、无广告、无数据上传；只有点击/打开后的指定测试请求。

## 构建方法

### Android Studio

1. 安装 **Android Studio**、Android SDK Platform 35、JDK 17、**Gradle 8.7**。
2. 将本文件夹作为 Android Studio 工程打开。若 IDE 提示缺少 Gradle Wrapper JAR，请选择使用本地 Gradle 8.7，或在本机执行 `gradle wrapper --gradle-version 8.7` 生成标准包装器（首次需要联网）。
3. 等待同步完成，使用 **Build → Build APK(s)**。输出位置：`app/build/outputs/apk/debug/app-debug.apk`。
4. 在 Android TV 上使用 ADB 安装：`adb install -r app/build/outputs/apk/debug/app-debug.apk`。

### GitHub Actions（无需在本机安装 Android SDK）

1. 将项目文件上传到一个 GitHub 仓库。
2. 打开 **Actions → Build Android debug APK → Run workflow**。
3. 构建结束后下载名为 `LinkScope-debug-apk` 的 Artifact，内有 `app-debug.apk`。

## 测试工作原理

| 项目 | 测试方式 | 主要判据 |
|---|---|---|
| ViuTV | `POST https://api.viu.now.com/p8/3/getLiveURL` | `responseCode=SUCCESS` / `GEO_CHECK_FAIL` |
| myTV SUPER | `GET https://www.mytvsuper.com/api/auth/getSession/self/` | `supported_country`, `country_code` |
| Gemini | `GET https://gemini.google.com/` | HTTP 状态、地区标识和区域不可用提示 |
| OpenAI | `GET https://chatgpt.com/cdn-cgi/trace` | Cloudflare trace 的 `ip`、`loc`，限制地区初步匹配 |
| HOY TV / RTHK | `HEAD` 直播 m3u8 | 200 / 403 |
| Netflix | 请求两个 Netflix 测试片目 | 正式/有限可用线索 |
| YouTube | 请求 Premium 页面 | 可达性和地区不可用线索 |
| Cloudflare | 获取 `cdn-cgi/trace` | 出口地区与 IP |
| GitHub | `HEAD https://github.com/` | 200–399 |

**重要限制：**

- 显示的 `ms` 是 HTTPS 请求响应耗时（HTTP latency），**不是 ICMP Ping 或真实网络 RTT**。
- IP 所在地区及接口响应无法保证登录、播放 DRM 内容、账号订阅和最终服务权限；AI 网站的可达性也不等于所有 API 可用。
- 网站随时会变更地区策略、响应体、API 或反爬机制，因此失败和待确认需要结合实际访问验证。
- 检测使用该 App 实际网络路径。其他 Mihomo/Clash 客户端可通过系统 VPN/TUN 接管此 App；若配置 HTTP 代理，将按指定代理端口发送 HTTPS CONNECT 请求，但不会直接切换 Mihomo 内部代理组。
- 当系统网络不能访问相应网站时，请先确认代理可达、DNS/TLS 工作正常。

## 源码及开源许可

本项目的网络探测 URL、响应判据参考并改写自 **appshubcc/Bettbox** (`lib/common/media_unlock_checker.dart`)，因此整个派生工程采用 **GPL-3.0** 授权；详见 `LICENSE`。界面和 Android 原生布局为独立编写。

- 源项目：https://github.com/appshubcc/Bettbox
- 项目许可：https://github.com/appshubcc/Bettbox/blob/main/LICENSE

本应用并非 Bettbox、ViuTV、myTV SUPER、Google 或 OpenAI 的官方产品。图标用于识别测试服务；应用启动图标为 LinkScope 原创。
