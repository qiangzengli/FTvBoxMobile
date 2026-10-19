# 参考 APK 的 Kotlin 功能核对

核对对象：用户提供的 `base.apk.1`。JADX 把 DEX 反编译为 `.java`，因此文件扩展名不能用来判断原始语言。APK 内的 `HomeFragment`、`SubscriptionActivity`、`FastSearchActivity`、`SettingActivity` 等类带 `kotlin.Metadata`；对应 Kotlin 源码位于上游 `app/src/main/java/com/github/tvbox/osc/ui/`。上游源码用于理解流程，具体行为仍以 APK 为准。

| 功能 | Kotlin 实现及 APK 核对 | 当前 Flutter 状态 |
| --- | --- | --- |
| 首页站源 | `HomeFragment.kt` 先 `loadConfig`，再从根配置 `spider` 调用 `loadJar`，随后 `getSort`；切换站源会重新加载首页 | Android 已接入 `type=3` DEX 宿主并实测首页与详情；iOS 可经 Android 局域网桥接使用该宿主 |
| 订阅 | `SubscriptionActivity.kt` 支持单线路、`urls` 多线路、`storeHouse` 多仓、本地文件、选择、置顶、重命名、删除及持久化 | 已有订阅管理、本地导入和多线路解析；应逐项验证多仓、置顶和持久化的行为差异 |
| 搜索 | `FastSearchActivity.kt` 对可搜索站源并发调用 `SourceViewModel.getSearch`，按站源分组和筛选，并保存搜索历史；无痕模式不保存 | Android Spider 搜索已接入；跨站结果分组仍需核对 |
| 设置 | `SettingActivity.kt` 包含播放器、解码、缩放、DNS、背景播放、无痕、首页推荐、备份及缓存设置 | 已有对应设置页，但依赖 Android 播放器或 Spider 的选项需要逐项做双平台实现和验证 |

## Spider 宿主进度与限制

当前订阅的 63 个站源均为 `type=3`、`csp_*`，共用根配置的 Spider 包。Android 已能加载 DEX，并在真机验证首页、分类、封面和详情。宿主按参考 APK 顺序调用 `initApi` 与 `init`，提供 `multiReq`、局域网转发、`proxy://` 转换和代理流接口。仍需对实际播放及部分 CatVod 宿主辅助类验收，因此不能认定全部站源可用。iOS 不直接执行 Android DEX/JNI，而是按 [iOS Spider 桥接说明](ios_spider_bridge.md) 使用 Android 设备。

用户指定的调试订阅 `http://tvbox.xn--4kq62z5rby2qupq9ub.top/` 在 2026-10-19 从当前网络访问时，HTTP 直连失败且代理返回 502；HTTPS 直连失败。地址恢复可访问后应重新进行端到端验收。

2026-10-19 在 iPhone 17 Pro（iOS 26.4）模拟器上运行应用与设备端自动化测试：空首页正常启动，未配置桥接时 Spider 站源不可选，播放器画面比例设置可保存；配置本地模拟桥接后，首页、详情、播放地址和代理 URL 转换通过，系统播放器可初始化真实 H.264 MP4 流。应用网络层请求上述订阅时解析到 `95.82.8.7`，连接被拒绝。真实站源仍需在有效订阅和在线 Android 设备上验收。

## 后续验收顺序

1. 先完成 Spider 宿主方案，验证该订阅至少一个站源的首页、分类、搜索、详情和播放。
2. 再验证其余站源及并发搜索，最后逐项核对订阅、设置和播放细节。
3. Android 与 iOS 分别做真实构建和设备验证；不能仅凭站源选项变为可点击就认定功能完成。
