# Full Repository Java to Kotlin Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert all 346 Java classes in `app`, `player`, and `quickjs` modules to idiomatic, type-safe, null-safe Kotlin code while maintaining 100% functional integrity, clean compilation, and passing all tests.

**Architecture:** We use a bottom-up, dependency-tiered strategy. First, we upgrade Gradle build configurations to support Kotlin and KAPT across all modules. Then we convert independent low-level modules (`quickjs`, `player`), followed by app base libraries, models/database/utilities, services/controllers, UI/activities, and unit tests. Each task verifies compilation with `./gradlew compileDebugSources`.

**Tech Stack:** Kotlin 2.2.20, Android Gradle Plugin 8.11.1, KAPT, Room 2.6.1, Glide 4.16.0, ExoPlayer / IjkPlayer JNI, QuickJS, EventBus 3.

**Spec:** `docs/superpowers/specs/2026-11-10-java-to-kotlin-migration-design.md`

## Global Constraints
- Target 0 remaining Java files: `find . -name "*.java" | grep -v "/build/"` must return empty at project completion.
- Binary & JNI Compatibility: Native methods must use `external fun`. JNI fields accessed by C code (`mNativeMediaPlayer`, `mListenerContext`, etc.) must be marked `@Keep` and `@JvmField`. JNI callbacks invoked by C code must be marked `@Keep` and `@JvmStatic`.
- Room Compatibility: Room DAOs and Entities must compile with `kapt`, using parameter defaults to ensure parameterless constructors where needed.
- Null Safety: No arbitrary `!!` force unwrapping; use safe calls `?.` and Elvis `?:` fallback values.
- Interop: Preserve `@JvmStatic` / `@JvmField` on public helper methods and constants during multi-task migration.

## Review Focus
- JNI method and field lookup failure: C++ `GetFieldID` or `GetStaticMethodID` crashing due to Kotlin property getters or companion object mangling.
- Room database schema mismatch or missing DAO implementations due to annotation processor not running on Kotlin sources.
- Deserialization regression: Gson/FastJson producing null fields because of missing parameterless constructors on Kotlin data classes.
- EventBus event delivery failure: Event subscriber methods becoming private or losing `@Subscribe`.
- Android Lifecycle visibility errors: `onCreate`, `onDestroy`, `onBackPressed` changing to `internal` or breaking overriding rules.

---

### Task 0: Infrastructure & Gradle Kotlin/KAPT Setup

**Files:**
- Modify: `player/build.gradle`
- Modify: `quickjs/build.gradle`
- Modify: `app/build.gradle`

**Interfaces:**
- Consumes: Existing Gradle plugin configuration in root `build.gradle`.
- Produces: Working Kotlin compilation and KAPT annotation processing in all three modules.

- [ ] **Step 1: Update `player/build.gradle` and `quickjs/build.gradle`**
Add `id 'org.jetbrains.kotlin.android'` to `plugins` block in both files.

- [ ] **Step 2: Update `app/build.gradle` for KAPT**
Add `id 'org.jetbrains.kotlin.kapt'` to plugins. Change `annotationProcessor` for Room and Glide to `kapt`:
```groovy
kapt 'androidx.room:room-compiler:2.6.1'
kapt 'com.github.bumptech.glide:compiler:4.16.0'
```

- [ ] **Step 3: Verify build baseline**
Run: `./gradlew compileDebugSources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**
```bash
git add build.gradle player/build.gradle quickjs/build.gradle app/build.gradle
git commit -m "build: configure kotlin and kapt across all modules"
```

---

### Task 1: Tier 1A - QuickJS Module Migration (5 files)

**Files:**
- Convert to `.kt` and remove `.java`:
  - `quickjs/src/main/java/com/whl/quickjs/wrapper/Function.java` -> `Function.kt`
  - `quickjs/src/main/java/com/whl/quickjs/wrapper/UriUtil.java` -> `UriUtil.kt`
  - `quickjs/src/main/java/com/whl/quickjs/wrapper/MapCreator.java` -> `MapCreator.kt`
  - `quickjs/src/main/java/com/whl/quickjs/wrapper/ContextSetter.java` -> `ContextSetter.kt`
  - `quickjs/src/main/java/com/whl/quickjs/wrapper/JSUtils.java` -> `JSUtils.kt`

**Interfaces:**
- Consumes: QuickJS native wrapper C libraries.
- Produces: Kotlin interfaces and utility methods with `@JvmStatic` / `@JvmField` for Java callers.

- [ ] **Step 1: Convert `Function`, `UriUtil`, `MapCreator`, `ContextSetter`, `JSUtils`**
Translate each file to idiomatic Kotlin, retaining exact method signatures and unchecked cast helpers.

- [ ] **Step 2: Remove old `.java` files**
```bash
rm quickjs/src/main/java/com/whl/quickjs/wrapper/*.java
```

- [ ] **Step 3: Verify QuickJS compilation**
Run: `./gradlew :quickjs:compileDebugSources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**
```bash
git add quickjs/src/main/java
git commit -m "refactor(quickjs): convert wrapper classes to Kotlin"
```

---

### Task 2: Tier 1B - Player Module: Utils, Render & Controllers (24 files)

**Files:**
- Convert to `.kt` and remove `.java`:
  - `player/src/main/java/xyz/doikki/videoplayer/util/CutoutUtil.java`, `PlayerUtils.java`, `L.java`
  - `player/src/main/java/xyz/doikki/videoplayer/render/IRenderView.java`, `TextureRenderView.java`, `TextureRenderViewFactory.java`, `MeasureHelper.java`, `RenderViewFactory.java`
  - `player/src/main/java/xyz/doikki/videoplayer/controller/IVideoController.java`, `IGestureComponent.java`, `ControlWrapper.java`, `MediaPlayerControl.java`, `GestureVideoController.java`, `BaseVideoController.java`, `IControlComponent.java`, `OrientationHelper.java`
  - `player/src/main/java/xyz/doikki/videoplayer/player/ProgressManager.java`, `AudioFocusHelper.java`, `PlayerFactory.java`, `VideoViewConfig.java`, `VideoViewManager.java`, `AbstractPlayer.java`, `VideoView.java`

- [ ] **Step 1: Convert util & render classes**
Convert `CutoutUtil`, `PlayerUtils`, `L`, and `render/*` to Kotlin.

- [ ] **Step 2: Convert controller & player classes**
Convert `controller/*` and `player/*` (including `VideoView` and `BaseVideoController`).

- [ ] **Step 3: Remove converted Java files**
Remove converted `.java` files in `xyz/doikki/videoplayer/`.

- [ ] **Step 4: Verify compilation**
Run: `./gradlew :player:compileDebugSources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**
```bash
git add player/src/main/java/xyz/doikki/videoplayer
git commit -m "refactor(player): convert util, render and controller classes to Kotlin"
```

---

### Task 3: Tier 1C - Player Module: ExoPlayer, IjkPlayer & IJK JNI Bindings (33 files)

**Files:**
- Convert to `.kt` and remove `.java`:
  - `player/src/main/java/xyz/doikki/videoplayer/exo/*` (5 files)
  - `player/src/main/java/xyz/doikki/videoplayer/ijk/*` (3 files)
  - `player/src/main/java/tv/danmaku/ijk/media/player/*` (all 25 files, including `IjkMediaPlayer`, `IMediaPlayer`, annotations, exceptions, ffmpeg, misc, pragma)

- [ ] **Step 1: Convert Exo and Ijk wrapper classes**
Convert `ExoMediaPlayer`, `ExoMediaPlayerFactory`, `ExoMediaSourceHelper`, `HlsErrorHandlingPolicy`, `OkHttpDataSource`, `IjkPlayer`, `IjkPlayerFactory`, `RawDataSourceProvider`.

- [ ] **Step 2: Convert `tv.danmaku.ijk.media.player` JNI classes**
Convert `IjkMediaPlayer` and related classes. Mark native methods with `external fun`, JNI-accessed fields with `@Keep` and `@JvmField`, and JNI callbacks with `@Keep` and `@JvmStatic`.

- [ ] **Step 3: Remove old Java files and verify player module**
Run: `./gradlew :player:compileDebugSources && ./gradlew compileDebugSources`
Expected: BUILD SUCCESSFUL; remaining Java files in `player` module is 0.

- [ ] **Step 4: Commit**
```bash
git add player/src/main/java
git commit -m "refactor(player): convert Exo, Ijk and JNI bindings to Kotlin"
```

---

### Task 4: Tier 2A - DnsOverHttps, P2P, Pyramid & Mobile Core (23 files)

**Files:**
- Convert to `.kt` and remove `.java`:
  - `app/src/main/java/okhttp3/dnsoverhttps/*` (3 files)
  - `app/src/main/java/com/undcover/freedom/pyramid/*` (5 files)
  - `app/src/main/java/com/p2p/P2PClass.java` (1 file)
  - `app/src/main/java/com/fbox/mobile/auth/*` (7 files)
  - `app/src/main/java/com/fbox/mobile/LegacyFlutterImport.java`
  - `app/src/main/java/com/fbox/mobile/LocalVideos.java`
  - `app/src/main/java/com/fbox/mobile/MobileHomeScroll.java`
  - `app/src/main/java/com/fbox/mobile/MobilePlayerControls.java`
  - `app/src/main/java/com/fbox/mobile/MobileSheets.java`

- [ ] **Step 1: Convert DNS, Pyramid and P2P**
Convert `okhttp3/dnsoverhttps/*`, `undcover/freedom/pyramid/*`, and `P2PClass`.

- [ ] **Step 2: Convert `com.fbox.mobile` auth and helpers**
Convert `auth/*`, `LegacyFlutterImport`, `LocalVideos`, `MobileHomeScroll`, `MobilePlayerControls`, `MobileSheets`.

- [ ] **Step 3: Remove old Java files and verify**
Run: `./gradlew compileDebugSources && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL and tests pass.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/okhttp3 app/src/main/java/com/undcover app/src/main/java/com/p2p app/src/main/java/com/fbox/mobile
git commit -m "refactor(app): convert dns, pyramid, p2p and mobile core to Kotlin"
```

---

### Task 5: Tier 2B - CatVod Crawler & Proxy System (27 files)

**Files:**
- Convert to `.kt` and remove `.java`:
  - `app/src/main/java/com/github/catvod/crawler/*` (24 files: `Spider`, `SpiderNull`, `SpiderDebug`, etc.)
  - `app/src/main/java/com/github/catvod/net/*` (2 files)
  - `app/src/main/java/com/github/catvod/Proxy.java` (1 file)

- [ ] **Step 1: Convert `Spider` interfaces and base implementations**
Convert `Spider.java` to Kotlin interface/abstract class with `@JvmOverloads` where necessary.

- [ ] **Step 2: Convert crawler implementations, net helpers and `Proxy`**
Convert remaining `crawler/*`, `net/*`, and `Proxy.java`.

- [ ] **Step 3: Remove old Java files and verify**
Run: `./gradlew compileDebugSources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/github/catvod
git commit -m "refactor(catvod): convert spider crawler and proxy to Kotlin"
```

---

### Task 6: Tier 3A - App Data Beans & Events (49 files)

**Files:**
- Convert to `.kt` and remove `.java`:
  - `app/src/main/java/com/github/tvbox/osc/bean/*` (~45 files: `Vod`, `Movie`, `SourceBean`, `LiveChannel`, `IJKCode`, etc.)
  - `app/src/main/java/com/github/tvbox/osc/event/*` (4 files: `RefreshEvent`, `ServerEvent`, etc.)

- [ ] **Step 1: Convert events to Kotlin data classes**
Convert `event/*` classes to Kotlin `data class` with properties.

- [ ] **Step 2: Convert bean models to Kotlin data classes**
Convert `bean/*` models, maintaining `@SerializedName`, `@Keep`, and default constructor parameters for Gson/FastJson compatibility.

- [ ] **Step 3: Remove old Java files and verify**
Run: `./gradlew compileDebugSources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/github/tvbox/osc/bean app/src/main/java/com/github/tvbox/osc/event
git commit -m "refactor(app): convert data beans and events to Kotlin"
```

---

### Task 7: Tier 3B - App Utilities & Room Database/DAOs (35 files)

**Files:**
- Convert to `.kt` and remove `.java`:
  - `app/src/main/java/com/github/tvbox/osc/data/*` (AppDataBase, DAOs, Migrations)
  - `app/src/main/java/com/github/tvbox/osc/util/*` (HawkConfig, MD5, FastClickCheck, FileUtils, etc.)

- [ ] **Step 1: Convert Room Database & DAOs**
Convert `AppDataBase` and all DAO interfaces. Verify Room schema compilation via `kapt`.

- [ ] **Step 2: Convert Utility classes**
Convert `HawkConfig`, `FastClickCheck`, `MD5`, `FileUtils`, `EpgUtil`, etc., using Kotlin `object` or `@JvmStatic` helper methods.

- [ ] **Step 3: Remove old Java files and verify**
Run: `./gradlew compileDebugSources`
Expected: BUILD SUCCESSFUL; Room schema generates cleanly.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/github/tvbox/osc/data app/src/main/java/com/github/tvbox/osc/util
git commit -m "refactor(app): convert Room database, DAOs and utilities to Kotlin"
```

---

### Task 8: Tier 4A - App Subtitle & Server Modules (20 files)

**Files:**
- Convert to `.kt` and remove `.java`:
  - `app/src/main/java/com/github/tvbox/osc/server/*` (NanoHTTPD server, ControlManager, etc.)
  - `app/src/main/java/com/github/tvbox/osc/subtitle/*` (Subtitle parsers, renderers)

- [ ] **Step 1: Convert `server/*` classes**
Translate NanoHTTPD handlers, local HTTP server, remote control socket server.

- [ ] **Step 2: Convert `subtitle/*` classes**
Translate subtitle parsers (SRT, ASS/SSA), subtitle styling, and rendering helpers.

- [ ] **Step 3: Remove old Java files and verify**
Run: `./gradlew compileDebugSources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/github/tvbox/osc/server app/src/main/java/com/github/tvbox/osc/subtitle
git commit -m "refactor(app): convert subtitle and local server modules to Kotlin"
```

---

### Task 9: Tier 4B - ViewModels, Callbacks & Player Components (40 files)

**Files:**
- Convert to `.kt` and remove `.java`:
  - `app/src/main/java/com/github/tvbox/osc/viewmodel/*`
  - `app/src/main/java/com/github/tvbox/osc/callback/*`
  - `app/src/main/java/com/github/tvbox/osc/player/*`
  - `app/src/main/java/com/github/tvbox/osc/cache/*`

- [ ] **Step 1: Convert ViewModels & Callbacks**
Translate ViewModels (`LiveViewModel`, `SourceViewModel`, etc.) and Callback interfaces.

- [ ] **Step 2: Convert app-level Player components & Cache**
Translate `player/*` (pip controller, custom video view wrappers) and `cache/*`.

- [ ] **Step 3: Remove old Java files and verify**
Run: `./gradlew compileDebugSources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/github/tvbox/osc/viewmodel app/src/main/java/com/github/tvbox/osc/callback app/src/main/java/com/github/tvbox/osc/player app/src/main/java/com/github/tvbox/osc/cache
git commit -m "refactor(app): convert viewmodels, callbacks and player components to Kotlin"
```

---

### Task 10: Tier 5A - UI Adapters, Dialogs & Custom Views (50 files)

**Files:**
- Convert to `.kt` and remove `.java`:
  - `app/src/main/java/com/github/tvbox/osc/ui/adapter/*`
  - `app/src/main/java/com/github/tvbox/osc/ui/dialog/*`
  - `app/src/main/java/com/github/tvbox/osc/ui/tv/*`

- [ ] **Step 1: Convert Adapters**
Convert RecyclerView adapters (`SeriesAdapter`, `LiveChannelAdapter`, `SearchAdapter`, etc.) with Kotlin ViewHolder patterns.

- [ ] **Step 2: Convert Dialogs and Custom Views**
Convert BottomSheets, Dialog fragments, TV widgets, focus listeners.

- [ ] **Step 3: Remove old Java files and verify**
Run: `./gradlew compileDebugSources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/github/tvbox/osc/ui/adapter app/src/main/java/com/github/tvbox/osc/ui/dialog app/src/main/java/com/github/tvbox/osc/ui/tv
git commit -m "refactor(app): convert adapters, dialogs and custom views to Kotlin"
```

---

### Task 11: Tier 5B - Fragments & Activities (40 files)

**Files:**
- Convert to `.kt` and remove `.java`:
  - `app/src/main/java/com/github/tvbox/osc/ui/fragment/*`
  - `app/src/main/java/com/github/tvbox/osc/ui/activity/*`
  - `app/src/main/java/com/github/tvbox/osc/base/*`

- [ ] **Step 1: Convert Base classes and Fragments**
Convert `BaseActivity`, `BaseFragment`, and all feature Fragments (`HomeFragment`, `SearchFragment`, `UserFragment`, etc.).

- [ ] **Step 2: Convert feature Activities**
Convert `MainActivity`, `DetailActivity`, `SearchActivity`, `LivePlayActivity`, `SettingActivity`, `FastSearchActivity`, etc.

- [ ] **Step 3: Remove old Java files and verify**
Run: `./gradlew compileDebugSources`
Expected: BUILD SUCCESSFUL; remaining Java files in `app/src/main` is 0.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/github/tvbox/osc/ui/fragment app/src/main/java/com/github/tvbox/osc/ui/activity app/src/main/java/com/github/tvbox/osc/base
git commit -m "refactor(app): convert activities and fragments to Kotlin"
```

---

### Task 12: Tier 6 - Unit Tests & Final Verification (4 test files)

**Files:**
- Convert to `.kt` and remove `.java`:
  - `app/src/test/java/com/fbox/mobile/AuthSessionTest.java` -> `AuthSessionTest.kt`
  - `app/src/test/java/com/fbox/mobile/ConfigAuthProviderTest.java` -> `ConfigAuthProviderTest.kt`
  - `app/src/test/java/com/fbox/mobile/LegacyFlutterImportTest.java` -> `LegacyFlutterImportTest.kt`
  - `app/src/test/java/com/fbox/mobile/NativeBehaviorTest.java` -> `NativeBehaviorTest.kt`

- [ ] **Step 1: Convert unit tests to Kotlin**
Translate tests to Kotlin using standard JUnit/Mock assertions.

- [ ] **Step 2: Remove old Java test files**
```bash
rm app/src/test/java/com/fbox/mobile/*.java
```

- [ ] **Step 3: Run comprehensive verification**
1. Check remaining Java files:
```bash
find . -name "*.java" | grep -v "/build/"
```
Expected output: EMPTY (0 files).

2. Run unit tests:
```bash
./gradlew testDebugUnitTest
```
Expected: BUILD SUCCESSFUL (all unit tests pass).

3. Assemble debug APK:
```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL, APK produced.

- [ ] **Step 4: Commit**
```bash
git add app/src/test/java/com/fbox/mobile
git commit -m "test: convert unit tests to Kotlin and complete repository migration"
```
