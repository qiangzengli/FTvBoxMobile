# FBox

FBox 是一个同时支持 Android 与 iOS 的 Flutter 应用，当前版本按 MBox 1.1.9 的手机端信息架构、页面层级、配色和主要交互复刻 UI。

## 运行

环境要求：Flutter 3.47.1 或兼容的稳定版本、Dart 3.13 及可用的 Android SDK / Xcode。

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

## 已实现页面

- FBox 品牌启动页、Android/iOS 原生启动图与应用图标
- 首页站源切换、分类标签、三列影片海报和底部“首页 / 我的”导航
- 快速搜索、热门关键词、搜索结果与影片详情
- 详情页完整选集分页、线路切换、收藏、投屏、下载与解析交互
- 全屏本地演示播放器，包括锁定、进度、倍速、字幕、弹幕、线路、投屏和设置面板
- 订阅新增/编辑/刷新/删除、观看历史、收藏、叠层直播、本地视频、链接播放、关于
- 设置页的主题色、浅色/深色、主页、聚合、背景、直播、DNS、隐私、播放器和数据选项
- Android/iOS 自适应本地文件入口，以及收藏、历史等会话内状态

## 当前边界

本阶段聚焦 UI 与本地交互。演示目录和频道均为本地数据；远程订阅解析、真实音视频播放、DLNA 投屏、文件扫描、下载、配置持久化和服务端接口尚未接入。相关按钮与页面已保留，便于后续连接真实模块。

应用状态集中在 `FBoxStore`，页面通过 `FBoxScope` 使用收藏、历史和主题行为；演示内容集中在 `DemoCatalog`，替换数据源时无需改动页面结构。

## 参考与归属

UI 参考包：MBox 1.1.9（`com.github.tvbox.osc`）。其“关于”页指向上游项目 [XiaoRanLiu3119/TVBoxOS-Mobile](https://github.com/XiaoRanLiu3119/TVBoxOS-Mobile)。发布或分发前，请确认上游项目许可证、品牌和内容源的合规要求。
