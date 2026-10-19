# iOS 使用 Android Spider 站源

这份订阅中的 Spider 包通过 Android DEX 和 ARM ELF `.so` 执行。iOS 应用通过同一局域网中的 Android 版 FBox 调用该包；封面、详情和播放器仍由 iOS 应用显示。

## iOS 独立运行的移植条件

独立运行是后续目标，当前桥接并未满足它。已保存的 Spider 包 `fbox-spider.bin` 中，公开 DEX 的 `BaseSpiderGuard` 仅把首页、分类、搜索、详情和播放调用转发给 `Init.getSpider`；`Init` 再通过 `DexNative.getLoader` 加载 `assets/wexshinidie.guard`。该包同时依赖 Android ARM 的 `wexguard_v8.so`。参考 APK 只包含 Spider 宿主，没有这批站源的可移植实现。

要在 iPhone 内执行这批站源，需要可访问的订阅配置，以及各站源的 Java/Kotlin 源码、可运行的 JavaScript 实现或公开 HTTP 协议。拿到协议后可在 Dart 中实现首页、分类、搜索、详情和播放解析，并以 Android 设备离线、iPhone 模拟器直连站源作为验收条件。当前给出的订阅地址在测试环境直连被拒绝，因此无法读取站源参数或验收真实内容。

## 连接

1. 在 Android 设备打开 FBox，进入“我的 → 设置 → 共享 Spider 给 iOS”，复制共享地址。保持 Android 应用运行。
2. 让 Android 设备与 iPhone 连接同一局域网。在 iPhone 的 FBox 中进入“我的 → 设置 → Android Spider 转发地址”，粘贴共享地址。保存后会检查连接。
3. 在 iPhone 导入同一份订阅，然后选站源。首页、分类、搜索、详情和播放地址会经 Android 设备调用 Spider；播放器及播放设置运行在 iPhone 上。

共享地址形如 `http://192.168.1.2:8793/<共享密钥>`。地址包含设备的局域网 IP；设备网络变化后应重新复制。Android 应用被系统结束时，转发服务也会停止。

## 当前验收范围

- iPhone 17 Pro 模拟器上的设备端测试已验证：站源选取、首页、详情、播放地址、`proxy://` 转换，以及 iOS 系统播放器从桥接形式的 `/proxy` 地址初始化真实的 H.264 MP4 流。Spider 响应使用本地模拟服务，不代表线上站源已通过。
- Android 调试 APK 已编译。Android 真机当前离线，现有 Android 模拟器缺少 `system.img`，因此局域网服务尚未在设备上实测。
- 用户提供的 `http://tvbox.xn--4kq62z5rby2qupq9ub.top/` 在 iOS 模拟器和当前主机的直连请求中被拒绝。地址恢复可用且 Android 设备在线后，仍需验证真实站源的封面、剧集、代理媒体流与播放设置。
