# FBox

FBox 是一个同时支持 Android 与 iOS 的 Flutter 媒体聚合应用。当前版本参考 MBox 1.1.9 的手机端信息架构和交互，已接入标准 MBox 配置、站源、直播、播放、离线下载、投屏及本地持久化能力。

## 运行

环境要求：Flutter 3.35.x 或更高兼容稳定版本、Dart 3.9+ 及可用的 Android SDK / Xcode。

```bash
flutter pub get
flutter run
```

常用验证命令：

```bash
flutter analyze
flutter test
flutter build apk --debug
flutter build ios --debug --no-codesign
```

Android 的 release 构建未使用调试签名；正式分发前请在本机配置私有 release keystore。iOS 真机分发同样需要在 Xcode 中配置开发团队与签名证书。

## 已实现功能

- FBox 品牌启动页、Android/iOS 原生启动图与应用图标
- HTTP 与本地 MBox JSON 配置导入、缓存、刷新、编辑、删除和站源切换，以及 JSON/XML 站源接口
- type 0、type 1 和 type 4 标准站源的分类、筛选、搜索、详情、线路及选集请求
- HTTP/HLS/本地文件视频播放，以及基于 WebView 媒体请求嗅探的解析页
- M3U/TXT 直播源解析、频道分组、直播播放及 DIYP JSON 节目单（EPG）
- 系统文件选择器导入本地视频
- 普通媒体与 HLS 分片下载、任务进度和离线文件管理
- DLNA 设备发现与 AVTransport 投屏，以及 iOS 原生 AirPlay 设备选择器
- 订阅、设置、收藏、历史和播放进度持久化
- JSON 数据备份导出与恢复
- HTTP 代理配置、DoH 查询诊断和网络异常提示
- 首页、搜索、详情、播放器、订阅、直播、本地视频、设置及媒体库完整交互

## 兼容性说明

- type 2/3 聚合解析及 Java/Python/QuickJS Spider 依赖原 Android Jar/运行时，无法直接跨平台复用；FBox 会显示兼容性诊断，建议将这类源转换为 type 4 HTTP Spider 服务。
- APK 内的闭源 P2P、迅雷和 Android IJK 二进制不能直接移植到 iOS；标准 HTTP/HLS 和系统支持的媒体格式可直接播放。
- 未提供真实字幕轨道或弹幕服务时，播放器会明确提示不支持，不显示模拟字幕或硬编码弹幕。
- Web 解析采用通用媒体请求嗅探，实际成功率取决于目标页面、鉴权和反自动化策略。
- DoH 与 HTTP 代理会用于 FBox 自身的订阅、站源、JSON 解析和下载请求；系统播放器、图片组件和 WebView 的媒体请求仍由操作系统网络栈处理，不能继承 Dart `HttpClient` 的代理或 DoH。
- 本地视频支持持久导入和应用目录扫描；Android 还可扫描用户明确选择的目录。受移动系统沙箱权限限制，应用不会绕过授权扫描整个设备文件系统。

应用状态集中在 `FBoxStore`，网络、配置、站源、下载、投屏和存储能力拆分在 `lib/services` 中。应用本身不内置内容源，播放能力和内容可用性取决于用户导入的合法配置与服务端。

## 参考与归属

UI 参考包：MBox 1.1.9（`com.github.tvbox.osc`）。其“关于”页指向上游项目 [XiaoRanLiu3119/TVBoxOS-Mobile](https://github.com/XiaoRanLiu3119/TVBoxOS-Mobile)。发布或分发前，请确认上游项目许可证、品牌和内容源的合规要求。
