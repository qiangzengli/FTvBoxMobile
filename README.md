# FBox Android 原生手机版

以用户提供的 `TVBox_q215613905_20260914-1520-java64 (1).apk` 为功能基准，采用其同版本 TVBoxOS 原生源码进行手机适配。来源和提交见 [UPSTREAM](UPSTREAM)，上游说明见 [UPSTREAM_README.md](UPSTREAM_README.md)。

仓库仅支持 Android 原生，已移除 Flutter/Dart、iOS 工程及 Flutter 构建依赖。用 Android Studio 直接打开仓库根目录即可。

界面使用 Jetpack Compose 导航、原生 Android Views/Fragments 和竖屏资源。保留原播放器、解析器和 Spider 接口，不通过 Flutter 调用播放功能。`app` 包含业务功能，`player` 包含原生播放器封装，`quickjs` 提供 JavaScript 运行时。

## 构建与安装

需要 JDK 17、Android SDK 36、Android SDK Platform Tools。当前构建为 arm64，与参考 java64 APK 一致。项目使用 Gradle 8.14.3、AGP 8.11.1、Kotlin 2.2.20。

在本目录创建不入库的 `local.properties`，设置 `sdk.dir=/你的/Android/sdk`，然后运行：

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :app:assembleDebug
adb -s 设备序列号 install -r app/build/outputs/apk/debug/app-debug.apk
```

Homebrew JDK 可以使用 `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`。不要使用 Java 25 运行该构建。

应用 ID 延续 `com.fbox.fbox`，版本为 `2.1.2-mobile`，版本代码为 5。调试包使用本机 Android 调试签名，可覆盖同签名 Flutter 调试版。签名不同不能直接覆盖。

### Release 安装包

已有发布签名可按 [签名备份说明](signing/README.md) 从加密备份恢复。也可将 `release-signing.properties.example` 复制为根目录的 `release-signing.properties`，填写自己的发布密钥路径、别名和密码。明文密钥、密码和实际配置均不入库；后续版本必须使用同一密钥才能覆盖安装。

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :app:assembleRelease
```

已配置签名时，安装包位于 `app/build/outputs/apk/release/app-release.apk`，启用 R8 混淆和优化，关闭调试。未配置签名时生成 `app-release-unsigned.apk`，不能直接安装。独立 release 签名不能覆盖现有调试版；切换前先在应用内备份数据。

从旧版升级时，首次启动会复制 Flutter 订阅地址、当前线路、数据源、收藏和历史到原生存储，保留原始 Flutter 偏好数据。播放进度和播放器设置暂不转换；原始数据仍保留。多订阅的源标识会映射为参考应用的源 key。

## 功能与手机操作

- 首页点击源名称切换数据源，更新分类和推荐；设置中切换配置或配置线路后自动重新加载首页。
- 首页底部提供搜索、直播和设置；顶部提供历史、收藏、链接播放和本地视频入口。分类支持点击与左右滑动，选中状态同步。首页“单列列表 / 多列海报”均向下滚动，切换设置返回首页立即生效。上滑内容时分类与快捷入口随滚动折叠，下滑展开；源选择和底部导航保持可见。
- 普通页面竖屏显示，海报网格适配手机宽度；搜索页不再显示重复标题栏，返回按钮与输入框同行。搜索使用系统键盘，输入框内可清空，键盘搜索键与页面按钮行为一致。搜索历史与热门搜索在同一个纵向滚动区域内展示，普通搜索结果返回后恢复建议页。历史和收藏改为列表，支持管理、清空和空状态提示。设置按功能分组，长页面可以滚动。普通搜索和聚合搜索均遵循“文字列表 / 海报网格”设置。
- 详情页支持预览、线路、选集、快速搜索和收藏；全屏转横屏，返回恢复竖屏。常用操作直接显示，完整资料和播放地址放在“简介”内，可选择复制。播放器提供“选集”和“更多”，高级设置无需遥控器操作。详情页预览区下方的资料、操作与选集可纵向滚动，短屏和放大字体下仍可访问完整内容。
- 原生保留 EXO/IJK/系统及外部播放器、网页嗅探、解析接口、Java/QuickJS Spider、字幕、弹幕、直播/EPG/回看、DLNA、远程推送、本地文件、备份恢复和网络设置。
- 直播横屏播放，顶部提供返回、频道、节目和设置按钮；保留滑动切换线路。
- 弹窗使用带标题、关闭按钮和下滑关闭手势的底部面板，横屏时使用侧边面板；输入表单适配软键盘。选集、筛选和长列表均保持可滚动；筛选项纵向换行，字幕和弹幕搜索支持键盘搜索键，长名称按手机宽度显示，清空操作提供明确的取消和确认按钮。
- 配置输入页去掉电视端扫码、远程输入和手动申请存储权限操作，提供输入、粘贴和选择文件；点播与直播地址独立保存。链接播放页提供输入、粘贴和播放按钮。
- 本地视频使用 Android 系统文件选择器，直接读取授权的文档 URI；订阅配置也使用系统文件选择器，导入到应用专属目录。
- 与 java64 基准一致，本构建没有 Python flavor。

## 验证

```sh
python3 tools/verify_layout_contracts.py
./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest
ANDROID_SERIAL=模拟器序列号 ./gradlew :app:connectedDebugAndroidTest
```

仪器测试会写入本地测试配置和收藏/历史，**应在专用模拟器运行**。测试服务器只监听模拟器的 localhost，不依赖在线订阅。测试覆盖网页/直链分流、本地文档 URI 解码、全屏返回、播放器高级操作、直播频道/设置、分类点击/滑动、配置软键盘及粘贴、首页两种布局、聚合搜索两种展示方式、首页滚动折叠与展开、搜索历史及热词完整滚动、普通搜索与返回、链接播放、22 类弹窗、搜索输入、收藏、数据源切换和旧版数据迁移。运行时截图保存在应用缓存 `native-*.png`。

2026-11-10 手机适配排查：27 项仪器测试已分批回归通过，2 项 JVM 测试通过。覆盖 API 35 arm64 的常规屏幕，以及 360 × 640 dp 小屏和 130% 字体；补充了 8 类弹窗长列表、120 集详情、筛选恢复、搜索返回和首页连续滚动检查。折叠过程中列表尺寸保持稳定，修复每帧改变高度引起的闪烁。已在 PKG110 覆盖安装 `2.1.2-mobile`（versionCode 5），升级前后订阅、历史及收藏存储保留。

布局契约以参考源码提交固定的控件清单为基准，同时检查全部 76 个默认布局和 76 个竖屏布局的原始 ID/类型。11 个 arm64 原生库与参考 APK 一致。已用此前报错的 dytt 网页地址完成实际 HLS 解析、解码及全屏返回验证。

原生功能从上游保留，不等于已验证所有在线服务。第三方 Spider、特定订阅、EPG/回看、字幕/弹幕服务、外部播放器和真实 DLNA 设备需要相应环境验证。当前仍使用参考应用的 target SDK 28；升级 target SDK 和商店发布不在这次兼容迁移中。

## 开源信息

本目录基于 TVBoxOS，遵循 [GNU AGPL v3](LICENSE)。保留上游署名与资源。修改包括手机布局、Compose 导航、构建配置和 Flutter 数据迁移，详见版本差异。应用“关于”提供本项目和上游地址。
