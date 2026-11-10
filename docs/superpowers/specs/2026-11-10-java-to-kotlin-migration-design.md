# Java to Kotlin Full Repository Migration Design

## 1. Executive Summary & Objective
- **Goal**: Convert 100% of the Java source files in the repository (`app`, `player`, `quickjs`) into clean, idiomatic, safe Kotlin code.
- **Constraints & Quality Standard**:
  - Zero functional regressions: all existing features (player playback via ExoPlayer/IJK, CatVod spider execution, Room database persistence, HTTP/Proxy services, mobile UI/sheets/settings) must continue operating without errors.
  - Zero compilation errors or broken annotations.
  - Final Java file count across modules must be exactly 0 (`find . -name "*.java" | grep -v "/build/"` returns empty).
  - All unit tests (`./gradlew testDebugUnitTest`) must pass.
  - Debug APK compilation (`./gradlew assembleDebug`) must succeed.

## 2. Current State & Baseline Metrics
- **Total Java Classes**: 346 files
  - `app` module: 284 Java files (UI, database, spiders, models, viewmodels, network, auth)
  - `player` module: 57 Java files (IJK player JNI wrapper, ExoPlayer wrapper, render views, controllers)
  - `quickjs` module: 5 Java files (QuickJS JNI context, utilities, reflection helpers)
- **Existing Kotlin Support**:
  - Root project `build.gradle` has Kotlin Gradle Plugin 2.2.20.
  - `app/build.gradle` has `org.jetbrains.kotlin.android` and compose plugins applied.
  - `player/build.gradle` and `quickjs/build.gradle` only apply `com.android.library` (need Kotlin plugin applied).
  - Existing Kotlin files: 1 (`app/src/main/java/com/fbox/mobile/MobileFrame.kt`).

## 3. Build & Gradle Configuration
1. **Module Gradle Updates**:
   - `player/build.gradle`: Apply `id 'org.jetbrains.kotlin.android'`.
   - `quickjs/build.gradle`: Apply `id 'org.jetbrains.kotlin.android'`.
   - Ensure `compileOptions` and `kotlinOptions` target Java 8 compatibility uniformly across all modules.
2. **Annotation Processing Updates (`app/build.gradle`)**:
   - Apply `id 'org.jetbrains.kotlin.kapt'`.
   - Upgrade `annotationProcessor` for Room (`androidx.room:room-compiler:2.6.1`) and Glide (`com.github.bumptech.glide:compiler:4.16.0`) to `kapt` so that Room DAOs/Entities and Glide modules authored in Kotlin are properly processed and generated.

## 4. Compatibility & Safety Conventions
1. **JNI / C++ Bindings (`player` & `quickjs`)**:
   - Native methods: Declare using Kotlin `external fun`.
   - C-accessed fields: Annotate with `@Keep` and `@JvmField` (e.g. `mNativeMediaPlayer`, `mListenerContext`, `mNativeMediaDataSource`, `mNativeAndroidIO` in `IjkMediaPlayer`) to guarantee no generated getter/setter indirection and identical field descriptors for `GetFieldID`.
   - C-invoked callbacks: Annotate with `@Keep` and `@JvmStatic` on companion object methods (e.g. `postEventFromNative`) to preserve standard static signatures for `GetStaticMethodID`.
2. **Room Database Compatibility**:
   - Room Entities (`@Entity`): Use properties with safe default values or explicit constructors.
   - Room DAOs (`@Dao`): Use Kotlin interfaces with standard Room annotations (`@Query`, `@Insert`, `@Update`, `@Delete`).
   - AppDataBase (`@Database`): Convert to Kotlin abstract class extending `RoomDatabase`.
3. **Serialization & Reflection (Gson / FastJson / EventBus)**:
   - Data classes: Provide default values for all properties to ensure a parameterless default constructor is generated for reflection-based deserializers.
   - Preserve `@SerializedName` annotations where field mapping differs from JSON keys.
   - EventBus: All event subscriber methods must be `fun` (public), retaining `@Subscribe(threadMode = ...)` annotations.
4. **Null Safety & Robustness**:
   - Eliminate unsafe force unwrap operators (`!!`). Use safe navigation (`?.`) and Elvis operators (`?:`) with sensible defaults.
   - Android lifecycle methods (`onCreate`, `onDestroy`, `onViewCreated`, `onBackPressed`) must retain exact visibility (`public` / `protected`).

## 5. Tiered Migration Plan

### Tier 1: Standalone Modules & Low-Level JNI (62 files)
- **Target**:
  - `quickjs`: 5 files (`QuickJSLoader`, `JSUtils`, `MapCreator`, `ContextSetter`, `UriUtil`, `Function`).
  - `player`: 57 files:
    - Utilities: `CutoutUtil`, `PlayerUtils`, `L`.
    - Render: `IRenderView`, `TextureRenderView`, `TextureRenderViewFactory`, `MeasureHelper`, `RenderViewFactory`.
    - Core Player: `AbstractPlayer`, `AudioFocusHelper`, `PlayerFactory`, `ProgressManager`, `VideoViewConfig`, `VideoViewManager`, `VideoView`.
    - Controllers: `BaseVideoController`, `ControlWrapper`, `GestureVideoController`, `IGestureComponent`, `IControlComponent`, `IVideoController`, `MediaPlayerControl`, `OrientationHelper`.
    - Exo: `ExoMediaPlayer`, `ExoMediaPlayerFactory`, `ExoMediaSourceHelper`, `HlsErrorHandlingPolicy`, `OkHttpDataSource`.
    - Ijk: `IjkPlayer`, `IjkPlayerFactory`, `RawDataSourceProvider`.
    - IJK JNI: `IMediaPlayer`, `AbstractMediaPlayer`, `IjkMediaPlayer`, `TextureMediaPlayer`, `MediaPlayerProxy`, `MediaInfo`, annotations, exceptions, ffmpeg, misc, pragma.
- **Verification**: `./gradlew compileDebugSources` and `./gradlew testDebugUnitTest`.

### Tier 2: App Standalone Libraries & Mobile Core (50 files)
- **Target**:
  - `okhttp3.dnsoverhttps`: 3 files (`BootstrapDns`, `DnsOverHttps`, `DnsRecordCodec`).
  - `com.undcover.freedom.pyramid`: 5 files (`Pyramid`, `PyramidContext`, `PyramidProxy`, etc.).
  - `com.p2p.P2PClass`: 1 file.
  - `com.github.catvod`: 27 files (`crawler/Spider`, `SpiderNull`, `SpiderDebug`, `Proxy`, `net/OKCallBack`, etc.).
  - `com.fbox.mobile`: 14 files (`LegacyFlutterImport`, `LocalVideos`, `MobileHomeScroll`, `MobilePlayerControls`, `MobileSheets`, `auth/*`).
- **Verification**: `./gradlew compileDebugSources` and `./gradlew testDebugUnitTest`.

### Tier 3: App Models, Database & Core Utilities (84 files)
- **Target**:
  - `com.github.tvbox.osc.bean`: ~45 data bean classes.
  - `com.github.tvbox.osc.data`: ~10 Room database & DAO classes.
  - `com.github.tvbox.osc.util`: ~25 utility classes (`HawkConfig`, `MD5`, `FastClickCheck`, `FileUtils`, etc.).
  - `com.github.tvbox.osc.event`: ~4 EventBus event classes.
- **Verification**: `./gradlew compileDebugSources` and `./gradlew testDebugUnitTest`.

### Tier 4: Business Logic, Services & Players in App (60 files)
- **Target**:
  - `com.github.tvbox.osc.server`: Local HTTP server & remote control manager.
  - `com.github.tvbox.osc.subtitle`: Subtitle parsers and renderers.
  - `com.github.tvbox.osc.player`: App-level video controller, player bridges, pip manager.
  - `com.github.tvbox.osc.viewmodel`: MVVM ViewModels.
  - `com.github.tvbox.osc.callback`: Network & callback listeners.
- **Verification**: `./gradlew compileDebugSources` and `./gradlew testDebugUnitTest`.

### Tier 5: App UI & Presentation Layer (90 files)
- **Target**:
  - `com.github.tvbox.osc.ui.activity`: All Activities (`MainActivity`, `DetailActivity`, `SearchActivity`, `LivePlayActivity`, `SettingActivity`, etc.).
  - `com.github.tvbox.osc.ui.fragment`: All Fragments.
  - `com.github.tvbox.osc.ui.adapter`: RecyclerView adapters.
  - `com.github.tvbox.osc.ui.dialog`: Custom dialogs and bottom sheets.
  - `com.github.tvbox.osc.ui.tv`: Custom TV views and UI widgets.
- **Verification**: `./gradlew compileDebugSources` and `./gradlew testDebugUnitTest`.

### Tier 6: Unit Tests Conversion (4 files)
- **Target**:
  - `AuthSessionTest`, `ConfigAuthProviderTest`, `LegacyFlutterImportTest`, `NativeBehaviorTest`.
- **Verification**: `./gradlew testDebugUnitTest`.

## 6. Verification and Acceptance Criteria
1. **Source Code**:
   - Zero Java files remain: `find . -name "*.java" | grep -v "/build/"` yields 0 lines.
2. **Build Success**:
   - `./gradlew compileDebugSources` passes cleanly with no syntax or annotation errors.
   - `./gradlew assembleDebug` successfully generates the debug APK (`app/build/outputs/apk/debug/app-debug.apk` or equivalent).
3. **Test Integrity**:
   - `./gradlew testDebugUnitTest` passes 100%.
