# Desktop 端迁移计划 — ComposeMediaPlayer 方案

## 背景

Desktop 端原先使用 VLCJ（VLC Java Bindings）实现视频和电台播放，但存在以下问题：
1. **需要安装 VLC**：用户必须手动安装 VLC 应用（`brew install vlc`），增加使用门槛
2. **原生依赖复杂**：VLCJ 需要加载 `.dylib`/`.so` 原生库，不同平台兼容性差
3. **Swing/AWT 集成**：VLCJ 的视频输出需要嵌入 Swing 组件，与 Compose UI 集成复杂
4. **macOS 兼容性**：VLCJ 在 macOS 上需要额外配置，且 VLC 的 macOS 版本更新频繁导致兼容问题

## 新方案：ComposeMediaPlayer

[ComposeMediaPlayer](https://github.com/kdroidFilter/composeMediaPlayer) 是一个纯 Kotlin/Compose 的跨平台媒体播放器库：
- **macOS**：底层使用 AVFoundation（原生硬件加速）
- **Windows**：底层使用 MediaFoundation
- **Linux**：底层使用 JavaFX MediaPlayer
- **无需安装任何第三方软件**
- **纯 Compose API**：`VideoPlayerSurface` 直接嵌入 Compose UI
- **跨平台一致 API**：同一套代码在 macOS/Windows/Linux 上运行

## 迁移步骤

### Phase 1: 数据层 — 移除 Room 数据库 ✅

Desktop 端不使用数据库缓存，所有数据直接从网络加载。

**改动文件：**
- `shared/src/jvmMain/kotlin/com/example/kmp_demo/core/data/paging/InMemoryPagingSource.kt` ✅ — 新增
- `shared/src/jvmMain/kotlin/com/example/kmp_demo/di/PlatformModule.jvm.kt` ✅ — 更新
- `shared/src/jvmMain/kotlin/com/example/kmp_demo/core/data/local/room/AppDatabaseBuilder.jvm.kt` ✅ — 删除

### Phase 2: 视频播放器 — VLCJ → ComposeMediaPlayer ✅

**改动文件：**
- `shared/src/jvmMain/kotlin/com/example/kmp_demo/core/player/platform/DesktopVideoPlayerController.kt` ✅ — 重写
- `shared/src/jvmMain/kotlin/com/example/kmp_demo/core/player/ui/PlatformVideoPlayerScreen.jvm.kt` ✅ — 重写

**实现细节：**
- `DesktopVideoPlayerController` 实现 `IPlayerController` 接口
- 内部持有 `VideoPlayerState`（由 Compose UI 层创建后注入）
- 通过轮询 `VideoPlayerState.currentTime/duration/sliderPos` 映射到 `StateFlow`
- `PlatformVideoPlayerScreen` 使用 `VideoPlayerSurface` 渲染视频
- 自定义控制栏覆盖在 `VideoPlayerSurface` 之上

### Phase 3: 电台播放器 — VLCJ → javax.sound ✅

**改动文件：**
- `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/radio/player/DesktopRadioPlayerController.kt` ✅ — 重写

**实现细节：**
- `DesktopRadioPlayerController` 实现电台 `IPlayerController` 接口
- 使用 Java 标准库 `javax.sound` API 播放音频流
- 支持 WAV/PCM 格式（原生），MP3 需要 SPI 解码器
- 使用 `Clip` 或 `SourceDataLine` 播放
- 支持播放列表管理（上一首/下一首）
- 无需任何第三方依赖，纯 JDK 标准库

### Phase 4: Repository — 无数据库实现 ✅

**改动文件：**
- `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/radio/data/repository/RadioRepositoryJvm.kt` ✅
- `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/domestic/data/repository/DomesticRepositoryJvm.kt` ✅
- `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/film/data/repository/FilmRepositoryJvm.kt` ✅

### Phase 5: DI 模块 ✅

**改动文件：**
- `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/radio/di/RadioModule.jvm.kt` ✅
- `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/domestic/di/DomesticModule.jvm.kt` ✅
- `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/film/di/FilmModule.jvm.kt` ✅

### Phase 6: Desktop UI 页面 — 网格列数适配 ✅

Desktop 端屏幕较大，首页瀑布流使用 5 列网格（Android 使用 2 列）。

**改动文件：**
- `shared/src/commonMain/kotlin/com/example/kmp_demo/core/components/GridConfig.kt` ✅ — 新增 `expect fun gridColumns(): Int`
- `shared/src/jvmMain/kotlin/com/example/kmp_demo/core/components/GridConfig.jvm.kt` ✅ — `actual fun gridColumns(): Int = 5`
- `shared/src/androidMain/kotlin/com/example/kmp_demo/core/components/GridConfig.android.kt` ✅ — `actual fun gridColumns(): Int = 2`
- `shared/src/commonMain/kotlin/com/example/kmp_demo/features/film/ui/FilmHomeScreen.kt` ✅ — `GridCells.Fixed(2)` → `GridCells.Fixed(gridColumns())`
- `shared/src/commonMain/kotlin/com/example/kmp_demo/features/domestic/ui/DomesticHomeScreen.kt` ✅ — `GridCells.Fixed(2)` → `GridCells.Fixed(gridColumns())`

**设计思路：**
- 使用 `expect`/`actual` 机制，在 `commonMain` 中声明平台无关的列数函数
- Desktop 返回 5 列，Android 返回 2 列
- UI 页面完全复用，只需改一行代码
- 搜索页面的网格列数同理，后续可按需修改

### Phase 7: 入口更新 ✅

**改动文件：**
- `desktopApp/src/main/kotlin/com/example/kmp_demo/main.kt` ✅

### 构建配置更新 ✅

**改动文件：**
- `gradle/libs.versions.toml` ✅ — 移除 VLCJ 依赖，保留 ComposeMediaPlayer
- `shared/build.gradle.kts` ✅ — 移除 VLCJ 依赖
- `desktopApp/build.gradle.kts` ✅ — 移除 VLCJ 依赖

## 对 Android 端的影响

**零影响。** 所有改动仅在 `jvmMain` 和 `desktopApp` 目录下：
- `commonMain` 的接口定义（`IPlayerController`、`VideoPlayerUiState` 等）保持不变
- `androidMain` 的 ExoPlayer 实现完全不受影响
- DI 模块各自独立（`platformModule` 在 Android 和 Desktop 上分别提供不同实现）

## 架构图

```
┌─────────────────────────────────────────────────────────┐
│                    commonMain                            │
│  ┌──────────────────────────────────────────────────┐   │
│  │  IPlayerController (interface)                    │   │
│  │  VideoPlayerUiState (data class)                  │   │
│  │  PlayerAction (sealed interface)                  │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
           ▲                              ▲
           │                              │
           │ implements                   │ implements
           │                              │
┌──────────┴──────────────┐  ┌───────────┴──────────────┐
│     jvmMain (Desktop)    │  │   androidMain (Android)   │
│  DesktopVideoPlayerCtrl  │  │  AndroidVideoPlayerCtrl   │
│  (ComposeMediaPlayer)    │  │  (ExoPlayer)              │
│  DesktopRadioPlayerCtrl  │  │  AndroidRadioPlayerCtrl   │
│  (ComposeMediaPlayer)    │  │  (Media3)                 │
└──────────────────────────┘  └──────────────────────────┘
```

## 优势总结

| 特性 | VLCJ（旧） | ComposeMediaPlayer（新） |
|------|-----------|------------------------|
| 安装依赖 | 需要安装 VLC | 无需安装任何软件 |
| 原生库 | 需要 `.dylib`/`.so` | 纯 Kotlin/Java |
| UI 集成 | SwingPanel + AWT | 原生 Compose |
| macOS 支持 | 需额外配置 | AVFoundation 原生 |
| Windows 支持 | 需安装 VLC | MediaFoundation 原生 |
| Linux 支持 | 需安装 VLC | JavaFX MediaPlayer |
| 跨平台 API | 不一致 | 一致 |
| 维护成本 | 高 | 低 |
