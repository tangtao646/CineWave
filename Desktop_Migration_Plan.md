# CineWave Desktop 端迁移计划

> 作者：资深 KMP 架构师
> 日期：2026-05-22
> 版本：v3.0（基于项目实际代码分析 + 播放器深度调研）

---

## 一、现状全景图

### 1.1 项目架构

```
CineWave/
├── shared/src/
│   ├── commonMain/          # 跨平台共享代码（Android + Desktop + iOS）
│   │   ├── App.kt                    # Android 入口（NavHost + 底部导航）
│   │   ├── di/AppModule.kt           # 通用 DI 模块（HttpClient, SensitiveWordFilter）
│   │   ├── di/PlatformModule.kt      # expect 平台模块声明
│   │   ├── core/
│   │   │   ├── data/remote/BasePagingRemoteMediator.kt  # RemoteMediator 基类（依赖 Room）
│   │   │   ├── data/local/BaseLocalDataSource.kt        # 本地数据源接口
│   │   │   ├── data/local/room/AppDatabase.kt           # Room 数据库（expect）
│   │   │   ├── data/local/room/BaseLocalDataSourceImpl.kt # Room 版 LocalDataSource 基类
│   │   │   └── components/PageContainer.kt              # 页面状态容器
│   │   └── features/
│   │       ├── domestic/    # 国内影视（ViewModel + Repository + UI）
│   │       ├── film/        # 电影（ViewModel + Repository + UI）
│   │       └── radio/       # 电台（ViewModel + Repository + UI）
│   │
│   ├── androidMain/         # Android 平台实现
│   │   ├── App.android.kt           # Android 入口（NavHost）
│   │   ├── di/PlatformModule.android.kt  # Room + ExoPlayer + Media3
│   │   └── core/player/
│   │       ├── platform/ExoPlayerController.kt  # ExoPlayer 视频播放器
│   │       ├── ui/PlatformVideoPlayerScreen.android.kt  # Android 视频播放 UI
│   │       └── ui/VideoPlayerSurface.android.kt  # PlayerView 渲染 Surface
│   │
│   └── jvmMain/             # Desktop 平台实现（已有部分）
│       ├── App.jvm.kt               # Desktop 入口（简易路由，复用 commonMain UI）
│       ├── di/PlatformModule.jvm.kt # Room + DesktopVideoPlayer + DesktopRadioPlayer
│       ├── core/
│       │   ├── data/local/room/AppDatabase.jvm.kt  # Room JVM 实现（SQLite JDBC）
│       │   ├── player/platform/DesktopVideoPlayerController.kt  # 视频播放器（占位）
│       │   └── player/ui/PlatformVideoPlayerScreen.jvm.kt      # 视频 UI（占位）
│       └── features/radio/player/DesktopRadioPlayerController.kt # 电台播放器（javax.sound）
│
└── desktopApp/              # Desktop 应用入口
    ├── build.gradle.kts
    └── src/main/kotlin/.../main.kt  # Koin 初始化 + Window
```

### 1.2 当前 Desktop 端的问题

| # | 问题 | 严重程度 | 说明 |
|---|------|---------|------|
| 1 | **Desktop 仍在使用 Room 数据库** | 🔴 阻塞 | `PlatformModule.jvm.kt` 注册了 `AppDatabase`，所有 feature DI 模块都注入了 Room DAO |
| 2 | **Desktop UI 直接复用 commonMain 的手机端 UI** | 🔴 阻塞 | `App.jvm.kt` 直接使用 `DomesticHomeScreen`、`FilmHomeScreen`、`RadioListScreen` 等手机端 Composable |
| 3 | **Desktop 路由使用简易状态管理** | 🟡 高 | 未使用 NavHost，路由切换通过 `when` 分支实现，无法支持深层导航 |
| 4 | **视频播放器只有占位实现** | 🔴 阻塞 | `DesktopVideoPlayerController` 和 `PlatformVideoPlayerScreen` 都是模拟实现，无法播放任何视频 |
| 5 | **电台播放器使用 javax.sound** | 🔴 阻塞 | `DesktopRadioPlayerController` 使用 `javax.sound`，对 MP3/AAC 流支持有限，macOS 上兼容性差 |
| 6 | **全屏控制器实现不完整** | 🟢 低 | `DesktopFullscreenController` 通过设置窗口大小模拟全屏 |

---

## 二、播放器深度调研

### 2.1 视频播放器方案对比

| 方案 | 平台兼容性 | 格式支持 | 性能 | 集成难度 | 许可证 | 生产评级 |
|------|-----------|---------|------|---------|--------|---------|
| **JavaFX MediaPlayer** | ✅ macOS ✅ Win ✅ Linux | MP4/H.264, AAC, MP3 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ (低) | GPL+CE | ✅ **推荐** |
| **VLCJ** | ✅ macOS ✅ Win ✅ Linux | 全格式（H.265, AV1, 等） | ⭐⭐⭐⭐⭐ | ⭐⭐ (中) | GPLv2 | ✅ **推荐** |
| **JavaCPP + FFmpeg** | ✅ macOS ✅ Win ✅ Linux | 全格式 | ⭐⭐⭐⭐⭐ | ⭐ (高) | GPLv2 | ⚠️ 备选 |
| **JCodec** | ✅ macOS ✅ Win ✅ Linux | H.264, MPEG-2 | ⭐⭐⭐ | ⭐⭐⭐⭐ (低) | BSD | ❌ 不推荐 |
| **Swing + Java2D** | ✅ 全平台 | 无原生解码 | ⭐⭐ | ⭐⭐⭐⭐ (低) | - | ❌ 不推荐 |

#### 方案 A：JavaFX MediaPlayer（推荐首选）

**优势**：
- Java 标准库的一部分（OpenJFX），无需额外安装原生库
- macOS 上使用 AVFoundation 后端，硬件加速
- 支持 HLS 流媒体（通过 `MediaPlayer`）
- 与 Compose Desktop 集成良好（通过 `SwingPanel` + `MediaView`）
- 许可证：GPL+CE（Classpath Exception），商业友好

**劣势**：
- 格式支持有限：仅 MP4/H.264、AAC、MP3
- 不支持 H.265/HEVC、AV1
- 不支持 DASH 流媒体
- 不支持字幕

**依赖**：
```kotlin
// build.gradle.kts (desktopApp)
dependencies {
    implementation("org.openjfx:javafx-media:21")  // 需指定平台
}
```

**macOS 兼容性**：
- macOS 10.15+ 原生支持
- 使用 AVFoundation 框架，硬件加速
- 支持 Apple Silicon (M1/M2/M3)

#### 方案 B：VLCJ（推荐备选）

**优势**：
- 全格式支持（H.264, H.265, AV1, VP9, 等）
- 支持 HLS、DASH、RTMP 等流媒体协议
- 硬件加速（VAAPI, VideoToolbox, CUDA）
- 支持字幕、音轨切换
- 成熟稳定，生产验证

**劣势**：
- 需要安装 VLC 原生库（macOS 上需要用户安装 VLC.app）
- 集成复杂度较高
- 许可证：GPLv2（可能影响商业分发）

**macOS 兼容性**：
- 需要用户安装 VLC.app（或捆绑 VLC.framework）
- 支持 Apple Silicon (M1/M2/M3)
- 通过 VideoToolbox 硬件加速

#### 方案 C：JavaCPP + FFmpeg（备选）

**优势**：
- 全格式支持
- 可定制性强
- 无需安装额外软件

**劣势**：
- 集成复杂度高
- 需要编译原生库
- 社区支持有限

### 2.2 电台播放器方案对比

| 方案 | 平台兼容性 | 格式支持 | 性能 | 集成难度 | 生产评级 |
|------|-----------|---------|------|---------|---------|
| **JavaFX MediaPlayer** | ✅ macOS ✅ Win ✅ Linux | MP3, AAC, WAV, OGG | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ (低) | ✅ **推荐** |
| **JLayer (MP3SPI)** | ✅ macOS ✅ Win ✅ Linux | MP3 | ⭐⭐⭐ | ⭐⭐⭐⭐ (低) | ✅ 推荐 |
| **javax.sound (当前)** | ⚠️ 有限 | WAV, AU, AIFF | ⭐⭐⭐ | ⭐⭐⭐⭐ (低) | ❌ 不推荐 |
| **VLCJ** | ✅ macOS ✅ Win ✅ Linux | 全格式 | ⭐⭐⭐⭐⭐ | ⭐⭐ (中) | ✅ 备选 |

#### 推荐方案：JavaFX MediaPlayer

**优势**：
- 原生支持 MP3、AAC、WAV、OGG
- 支持网络流媒体（HTTP/HTTPS）
- 支持 ICY 元数据（电台当前播放歌曲名）
- macOS 上使用 AVFoundation，低延迟
- 与视频播放器统一技术栈

**依赖**：
```kotlin
// build.gradle.kts (desktopApp)
dependencies {
    implementation("org.openjfx:javafx-media:21")
}
```

### 2.3 最终技术选型

| 组件 | 方案 | 理由 |
|------|------|------|
| **视频播放器** | **JavaFX MediaPlayer** | 无需原生库、macOS 硬件加速、与 Compose Desktop 集成良好 |
| **电台播放器** | **JavaFX MediaPlayer** | 统一技术栈、支持 MP3/AAC 流、支持 ICY 元数据 |
| **全屏控制器** | **Compose Desktop WindowState** | 原生支持，无需额外依赖 |

---

## 三、播放器架构设计

### 3.1 视频播放器架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Desktop 视频播放器架构                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              PlatformVideoPlayerScreen.jvm.kt       │   │
│  │  ┌───────────────────────────────────────────────┐  │   │
│  │  │  SwingPanel + JavaFX MediaView (视频渲染)     │  │   │
│  │  └───────────────────────────────────────────────┘  │   │
│  │  ┌───────────────────────────────────────────────┐  │   │
│  │  │  Compose 控制栏 (VideoPlayerControls)         │  │   │
│  │  └───────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              DesktopVideoPlayerController            │   │
│  │  ┌───────────────────────────────────────────────┐  │   │
│  │  │  JavaFX MediaPlayer 封装                      │  │   │
│  │  │  - 状态管理 (StateFlow)                       │  │   │
│  │  │  - 位置轮询                                   │  │   │
│  │  │  - 音量控制                                   │  │   │
│  │  └───────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 电台播放器架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Desktop 电台播放器架构                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │           DesktopRadioPlayerController               │   │
│  │  ┌───────────────────────────────────────────────┐  │   │
│  │  │  JavaFX MediaPlayer 封装                      │  │   │
│  │  │  - 流媒体播放 (MP3/AAC)                       │  │   │
│  │  │  - ICY 元数据解析                             │  │   │
│  │  │  - 播放列表管理                               │  │   │
│  │  └───────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │           RadioPlayerManager (复用 commonMain)       │   │
│  │  - 业务编排                                         │   │
│  │  - 睡眠定时器                                       │   │
│  │  - UI 状态聚合                                      │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 JavaFX 与 Compose Desktop 集成方案

Compose Desktop 使用 `SwingPanel` 可以嵌入 Swing/AWT 组件，而 JavaFX 的 `MediaView` 是 Swing 兼容的。集成方式：

```kotlin
@Composable
fun JavaFXVideoView(
    mediaPlayer: javafx.scene.media.MediaPlayer,
    modifier: Modifier = Modifier
) {
    // 使用 SwingPanel 嵌入 JavaFX MediaView
    SwingPanel(
        modifier = modifier,
        factory = {
            // 创建 JavaFX MediaView
            val mediaView = javafx.scene.media.MediaView(mediaPlayer)
            val group = javafx.scene.Group(mediaView)
            val scene = javafx.scene.Scene(group)
            
            // 创建 JFXPanel（Swing 组件）
            val jfxPanel = javax.swing.JFXPanel()
            jfxPanel.scene = scene
            
            // 设置 MediaView 自适应大小
            mediaView.fitWidthProperty().bind(scene.widthProperty())
            mediaView.fitHeightProperty().bind(scene.heightProperty())
            mediaView.isPreserveRatio = true
            
            jfxPanel
        },
        update = { jfxPanel ->
            // 更新时重新绑定
        }
    )
}
```

---

## 四、改造目标

### 4.1 核心目标

1. **Desktop 端跳过 Room 数据库缓存**，直接使用 PagingSource 加载网络数据
2. **Desktop 端拥有独立的 UI 页面**，适配宽屏、鼠标交互的桌面端设计范式
3. **Desktop 端使用 JavaFX MediaPlayer 实现视频和电台播放**，生产级可用
4. **不修改 commonMain 现有代码**，所有改动在 jvmMain 中完成
5. **不影响 Android 端**，Android 继续使用 Room + RemoteMediator + ExoPlayer

### 4.2 架构设计

```
shared/src/
├── commonMain/                    # 保持现有代码不变（Android/iOS 共用）
│   └── features/
│       ├── domestic/              # 接口 + Domain Model + ViewModel（复用）
│       │   ├── domain/repository/DomesticRepository.kt  # 接口
│       │   ├── domain/model/DomesticMedia.kt            # 数据模型
│       │   └── ui/DomesticViewModel.kt                  # ViewModel（复用）
│       ├── film/                  # 同上
│       └── radio/                 # 同上
│
├── jvmMain/                       # Desktop 专属
│   ├── App.jvm.kt                 # 🆕 重写：左侧导航 + NavHost + Desktop UI
│   ├── di/
│   │   ├── PlatformModule.jvm.kt  # 🆕 精简：移除 Room，保留播放器
│   │   ├── DomesticModule.jvm.kt  # 🆕 新增：无数据库的 Domestic DI
│   │   ├── FilmModule.jvm.kt      # 🆕 新增：无数据库的 Film DI
│   │   └── RadioModule.jvm.kt     # 🆕 新增：无数据库的 Radio DI
│   ├── core/
│   │   ├── data/paging/
│   │   │   └── InMemoryPagingSource.kt  # 🆕 内存分页数据源
│   │   └── player/
│   │       ├── platform/
│   │       │   └── DesktopVideoPlayerController.kt  # 🆕 重写：基于 JavaFX MediaPlayer
│   │       └── ui/
│   │           └── PlatformVideoPlayerScreen.jvm.kt  # 🆕 重写：SwingPanel + MediaView
│   └── features/
│       ├── domestic/
│       │   ├── data/repository/DomesticRepositoryJvm.kt  # 🆕 无数据库 Repository
│       │   └── ui/                # 🆕 Desktop UI 页面
│       │       ├── DomesticHomeScreen.kt
│       │       ├── DomesticDetailScreen.kt
│       │       ├── DomesticSearchScreen.kt
│       │       └── components/
│       │           └── DomesticMediaCard.kt
│       ├── film/
│       │   ├── data/repository/FilmRepositoryJvm.kt      # 🆕 无数据库 Repository
│       │   └── ui/                # 🆕 Desktop UI 页面
│       │       ├── FilmHomeScreen.kt
│       │       ├── FilmDetailScreen.kt
│       │       ├── FilmSearchScreen.kt
│       │       └── components/
│       │           └── MovieCard.kt
│       └── radio/
│           ├── data/repository/RadioRepositoryJvm.kt     # 🆕 无数据库 Repository
│           ├── player/
│           │   └── DesktopRadioPlayerController.kt       # 🆕 重写：基于 JavaFX MediaPlayer
│           └── ui/                # 🆕 Desktop UI 页面
│               ├── RadioListScreen.kt
│               ├── RadioSearchScreen.kt
│               └── components/
│                   ├── RadioStationCard.kt
│                   └── MiniPlayerBar.kt
│
├── androidMain/                   # 保持现有代码不变
└── iosMain/                       # 保持现有代码不变
```

---

## 五、详细实施步骤

### Phase 1: 基础设施 — 内存分页数据源

**目标**：提供不依赖 Room 的 PagingSource 实现，供 Desktop Repository 使用。

#### Step 1.1: 创建 `InMemoryPagingSource`

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/core/data/paging/InMemoryPagingSource.kt`

```kotlin
package com.example.kmp_demo.core.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * JVM 平台的内存分页数据源。
 *
 * 替代 Room PagingSource，数据直接通过 [fetchPage] 回调从网络获取，
 * 并在内存中缓存已加载的页面。
 *
 * 使用场景：Desktop 端不需要持久化缓存，每次启动都是全新会话。
 *
 * @param T 数据类型
 * @param fetchPage 分页加载回调，(page, pageSize) -> List<T>
 */
class InMemoryPagingSource<T : Any>(
    private val fetchPage: suspend (page: Int, pageSize: Int) -> List<T>
) : PagingSource<Int, T>() {

    private val cache = mutableMapOf<Int, List<T>>()

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: 1
        return try {
            val data = cache.getOrPut(page) { fetchPage(page, params.loadSize) }
            LoadResult.Page(
                data = data,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (data.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    /** 清除内存缓存并触发刷新 */
    fun invalidateCache() {
        cache.clear()
        invalidate()
    }
}
```

#### Step 1.2: 更新 `PlatformModule.jvm.kt` — 移除 Room

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/di/PlatformModule.jvm.kt`

```kotlin
package com.example.kmp_demo.di

import com.example.kmp_demo.core.player.cache.DiskLruCache
import com.example.kmp_demo.core.player.cache.M3u8CacheInterceptor
import com.example.kmp_demo.core.player.domain.IPlayerController as VideoPlayerController
import com.example.kmp_demo.core.player.platform.DesktopVideoPlayerController
import com.example.kmp_demo.features.radio.domain.player.IPlayerController as RadioPlayerController
import com.example.kmp_demo.features.radio.player.DesktopRadioPlayerController
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop (JVM) 平台特定的 Koin 模块
 *
 * ⚠️ 注意：Desktop 端不使用 Room 数据库。
 * 所有数据直接从网络加载，不持久化。
 */
actual val platformModule: Module = module {
    // ❌ 移除 Room Database — Desktop 不需要数据库缓存

    // === Disk Cache ===
    single<DiskLruCache> {
        val cacheDir = "${System.getProperty("user.home")}/.cinewave/video_cache"
        DiskLruCache(cacheDir = cacheDir)
    }

    // === M3U8 Cache Interceptor ===
    single<M3u8CacheInterceptor> {
        val cacheDir = "${System.getProperty("user.home")}/.cinewave/video_cache"
        M3u8CacheInterceptor(
            httpClient = get(),
            diskCache = get(),
            cacheDir = cacheDir,
        )
    }

    // === Video Player Controller (JavaFX MediaPlayer) ===
    single<VideoPlayerController> {
        DesktopVideoPlayerController(diskCache = get())
    }

    // === Radio Player Controller (JavaFX MediaPlayer) ===
    single<RadioPlayerController> {
        DesktopRadioPlayerController()
    }
}
```

#### Step 1.3: 删除 `AppDatabase.jvm.kt`

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/core/data/local/room/AppDatabase.jvm.kt`（删除）

---

### Phase 2: 视频播放器 — JavaFX MediaPlayer 实现

**目标**：使用 JavaFX MediaPlayer 实现 Desktop 端生产级视频播放器。

#### Step 2.1: 重写 `DesktopVideoPlayerController`

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/core/player/platform/DesktopVideoPlayerController.kt`

```kotlin
package com.example.kmp_demo.core.player.platform

import com.example.kmp_demo.core.player.cache.DiskLruCache
import com.example.kmp_demo.core.player.domain.IPlayerController
import com.example.kmp_demo.core.player.domain.VideoPlaybackState
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Desktop 视频播放器 — 基于 JavaFX MediaPlayer
 *
 * 使用 JavaFX MediaPlayer 实现视频播放，支持：
 * - MP4/H.264 视频
 * - HLS 流媒体（.m3u8）
 * - AAC/MP3 音频
 * - 音量控制
 * - 进度控制
 *
 * macOS 兼容性：
 * - 使用 AVFoundation 后端，硬件加速
 * - 支持 Apple Silicon (M1/M2/M3)
 * - 无需额外安装原生库
 *
 * 注意：JavaFX MediaPlayer 不支持 H.265/AV1，如需支持请改用 VLCJ。
 */
class DesktopVideoPlayerController(
    private val diskCache: DiskLruCache? = null
) : IPlayerController {

    companion object {
        private const val TAG = "DesktopVideoPlayerController"
        private const val POLL_INTERVAL_MS = 250L
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var positionPollingJob: Job? = null

    /** JavaFX MediaPlayer 实例，供 PlatformVideoPlayerScreen 绑定 */
    var mediaPlayer: MediaPlayer? = null
        private set

    private val _playbackState = MutableStateFlow(VideoPlaybackState.IDLE)
    override val playbackState: StateFlow<VideoPlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isFullScreen = MutableStateFlow(false)
    override val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    private val _bufferedPercent = MutableStateFlow(0)
    override val bufferedPercent: StateFlow<Int> = _bufferedPercent.asStateFlow()

    override suspend fun open(url: String, headers: Map<String, String>?) {
        _playbackState.value = VideoPlaybackState.BUFFERING

        // 释放旧的播放器
        releaseMediaPlayer()

        try {
            // 创建 JavaFX Media 对象
            val media = Media(url)
            val player = MediaPlayer(media)

            // 注册监听器
            player.setOnReady {
                _duration.value = player.totalDuration.toMillis()
                _playbackState.value = VideoPlaybackState.READY
                startPositionPolling()
            }

            player.setOnPlaying {
                _playbackState.value = VideoPlaybackState.PLAYING
            }

            player.setOnPaused {
                _playbackState.value = VideoPlaybackState.PAUSED
            }

            player.setOnStalled {
                _playbackState.value = VideoPlaybackState.BUFFERING
            }

            player.setOnEndOfMedia {
                _playbackState.value = VideoPlaybackState.ENDED
            }

            player.setOnError {
                _playbackState.value = VideoPlaybackState.ERROR
            }

            // 同步音量
            player.volume = _volume.value.toDouble()

            mediaPlayer = player
            player.play()
        } catch (e: Exception) {
            _playbackState.value = VideoPlaybackState.ERROR
        }
    }

    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null) {
                    _currentPosition.value = player.currentTime.toMillis()
                    val dur = player.totalDuration.toMillis()
                    if (dur > 0) {
                        _duration.value = dur
                        val buffered = player.bufferProgressTime.toMillis()
                        _bufferedPercent.value =
                            ((buffered.toFloat() / dur) * 100).toInt().coerceIn(0, 100)
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override suspend fun play() {
        mediaPlayer?.play()
    }

    override suspend fun pause() {
        mediaPlayer?.pause()
    }

    override suspend fun togglePlayPause() {
        val player = mediaPlayer
        if (player != null) {
            when (player.status) {
                MediaPlayer.Status.PLAYING -> player.pause()
                else -> player.play()
            }
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        mediaPlayer?.seek(javafx.util.Duration.millis(positionMs.toDouble()))
    }

    override suspend fun seekForward(seconds: Long) {
        val player = mediaPlayer ?: return
        val newPos = (player.currentTime.toMillis() + seconds * 1000)
            .coerceAtMost(player.totalDuration.toMillis())
        player.seek(javafx.util.Duration.millis(newPos))
    }

    override suspend fun seekBackward(seconds: Long) {
        val player = mediaPlayer ?: return
        val newPos = (player.currentTime.toMillis() - seconds * 1000).coerceAtLeast(0)
        player.seek(javafx.util.Duration.millis(newPos))
    }

    override suspend fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0f, 1f)
        mediaPlayer?.volume = _volume.value.toDouble()
    }

    override suspend fun toggleFullScreen() {
        _isFullScreen.value = !_isFullScreen.value
    }

    override fun release() {
        positionPollingJob?.cancel()
        scope.cancel()
        releaseMediaPlayer()
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.dispose()
        mediaPlayer = null
    }
}
```

#### Step 2.2: 重写 `PlatformVideoPlayerScreen.jvm.kt`

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/core/player/ui/PlatformVideoPlayerScreen.jvm.kt`

```kotlin
package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState
import com.example.kmp_demo.core.player.platform.DesktopVideoPlayerController
import org.koin.compose.koinInject
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.MediaView

/**
 * Desktop 平台视频播放器屏幕 — 基于 JavaFX MediaPlayer
 *
 * 使用 SwingPanel 嵌入 JavaFX MediaView 实现视频渲染。
 * 控制栏使用 Compose 原生组件，与 Android 端保持一致。
 *
 * macOS 兼容性：
 * - 使用 JavaFX MediaPlayer（AVFoundation 后端）
 * - 支持 Apple Silicon (M1/M2/M3)
 * - 无需额外安装原生库
 *
 * 依赖：
 * - org.openjfx:javafx-media:21
 * - org.openjfx:javafx-swing:21
 */
@Composable
actual fun PlatformVideoPlayerScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
    headers: Map<String, String>?,
    controls: @Composable BoxScope.(state: VideoPlayerUiState, onAction: (PlayerAction) -> Unit) -> Unit,
    topBar: @Composable (BoxScope.() -> Unit)?,
    onFullScreenChange: ((Boolean) -> Unit)?,
) {
    val controller = koinInject<DesktopVideoPlayerController>()
    val manager = remember { VideoPlayerManager(controller) }
    val uiState by manager.uiState.collectAsState()

    // 打开视频
    LaunchedEffect(url) {
        manager.open(url, headers)
    }

    // 全屏状态变化
    LaunchedEffect(uiState.isFullScreen) {
        onFullScreenChange?.invoke(uiState.isFullScreen)
    }

    // 释放资源
    DisposableEffect(manager) {
        onDispose {
            manager.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // JavaFX MediaView 视频渲染
        JavaFXVideoView(
            mediaPlayer = controller.mediaPlayer,
            modifier = Modifier.fillMaxSize()
        )

        // 覆盖层（控制栏 + 顶栏）
        Box(modifier = Modifier.fillMaxSize()) {
            // 中央播放/暂停按钮
            CenterPlayButton(
                state = uiState,
                onClick = { manager.togglePlayPause() },
                modifier = Modifier.align(Alignment.Center)
            )

            // 底部控制栏
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                controls(uiState) { action ->
                    handlePlayerAction(manager, action)
                }
            }

            // 顶部栏
            topBar?.let {
                Box(modifier = Modifier.align(Alignment.TopCenter)) {
                    it()
                }
            }
        }
    }
}

/**
 * JavaFX MediaView 的 Compose 封装
 *
 * 使用 SwingPanel 嵌入 JFXPanel，再在 JFXPanel 中创建 JavaFX Scene + MediaView。
 */
@Composable
fun JavaFXVideoView(
    mediaPlayer: javafx.scene.media.MediaPlayer?,
    modifier: Modifier = Modifier
) {
    // 使用 SwingPanel 嵌入 JavaFX
    SwingPanel(
        modifier = modifier,
        factory = {
            JFXPanel().apply {
                // 在 JavaFX 线程中初始化
                Platform.runLater {
                    val mediaView = MediaView(mediaPlayer)
                    mediaView.isPreserveRatio = true
                    
                    val root = StackPane(mediaView)
                    root.style = "-fx-background-color: black;"
                    
                    val scene = Scene(root)
                    this.scene = scene
                    
                    // 绑定 MediaView 大小到 Scene
                    mediaView.fitWidthProperty().bind(scene.widthProperty())
                    mediaView.fitHeightProperty().bind(scene.heightProperty())
                }
            }
        },
        update = { jfxPanel ->
            // 更新 MediaPlayer 实例
            Platform.runLater {
                val scene = jfxPanel.scene
                if (scene != null) {
                    val root = scene.root as StackPane
                    if (root.children.isNotEmpty()) {
                        val mediaView = root.children[0] as MediaView
                        mediaView.mediaPlayer = mediaPlayer
                    }
                }
            }
        }
    )
}

internal fun handlePlayerAction(
    manager: VideoPlayerManager,
    action: PlayerAction,
) {
    when (action) {
        PlayerAction.TogglePlayPause -> manager.togglePlayPause()
        is PlayerAction.SeekForward -> manager.seekForward(action.seconds)
        is PlayerAction.SeekBackward -> manager.seekBackward(action.seconds)
        is PlayerAction.SeekToFraction -> {
            val targetMs = (action.fraction * (manager.uiState.value.duration)).toLong()
            manager.seekTo(targetMs)
        }
        is PlayerAction.SeekToMs -> manager.seekTo(action.positionMs)
        PlayerAction.ToggleFullScreen -> manager.toggleFullScreen()
        PlayerAction.ToggleControls -> manager.toggleControls()
        else -> {}
    }
}
```

---

### Phase 3: 电台播放器 — JavaFX MediaPlayer 实现

**目标**：使用 JavaFX MediaPlayer 替换 javax.sound，实现生产级电台播放。

#### Step 3.1: 重写 `DesktopRadioPlayerController`

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/radio/player/DesktopRadioPlayerController.kt`

```kotlin
package com.example.kmp_demo.features.radio.player

import com.example.kmp_demo.features.radio.domain.player.AppPlaybackState
import com.example.kmp_demo.features.radio.domain.player.IPlayerController
import com.example.kmp_demo.features.radio.domain.player.MediaMetadataInfo
import com.example.kmp_demo.features.radio.domain.player.PlayableMedia
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Desktop 电台播放器 — 基于 JavaFX MediaPlayer
 *
 * 使用 JavaFX MediaPlayer 实现电台流媒体播放，支持：
 * - MP3 流媒体（HTTP/HTTPS）
 * - AAC 流媒体
 * - ICY 元数据（当前播放歌曲名）
 * - 播放列表管理
 *
 * macOS 兼容性：
 * - 使用 AVFoundation 后端，低延迟
 * - 支持 Apple Silicon (M1/M2/M3)
 * - 无需额外安装原生库
 *
 * 替代 javax.sound 的原因：
 * - javax.sound 不支持 MP3 解码
 * - javax.sound 在 macOS 上兼容性差
 * - JavaFX MediaPlayer 是生产级方案
 */
class DesktopRadioPlayerController : IPlayerController {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var currentPlaylist = listOf<PlayableMedia>()
    private var currentIndex = -1
    private var mediaPlayer: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentMediaId = MutableStateFlow<String?>(null)
    override val currentMediaId: StateFlow<String?> = _currentMediaId.asStateFlow()

    private val _playbackState = MutableStateFlow(AppPlaybackState.IDLE)
    override val playbackState: StateFlow<AppPlaybackState> = _playbackState.asStateFlow()

    private val _metadataInfo = MutableStateFlow(MediaMetadataInfo())
    override val metadataInfo: StateFlow<MediaMetadataInfo> = _metadataInfo.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    override suspend fun setPlaylist(items: List<PlayableMedia>, startIndex: Int) {
        currentPlaylist = items
        currentIndex = startIndex
        if (startIndex in items.indices) {
            loadAndPlay(items[startIndex])
        }
    }

    private fun loadAndPlay(item: PlayableMedia) {
        releaseCurrentPlayer()
        _currentMediaId.value = item.id
        _playbackState.value = AppPlaybackState.BUFFERING

        try {
            val media = Media(item.uri)
            val player = MediaPlayer(media)

            player.setOnReady {
                _playbackState.value = AppPlaybackState.READY
                _isPlaying.value = true
                player.play()
            }

            player.setOnPlaying {
                _isPlaying.value = true
                _playbackState.value = AppPlaybackState.READY
            }

            player.setOnPaused {
                _isPlaying.value = false
            }

            player.setOnStalled {
                _playbackState.value = AppPlaybackState.BUFFERING
            }

            player.setOnError {
                _errorEvents.tryEmit("播放失败: ${player.error?.errorMessage ?: "未知错误"}")
                _playbackState.value = AppPlaybackState.ERROR
            }

            // 监听元数据（ICY 元数据）
            player.setOnMetadata {
                val metadata = player.media.metadata
                val title = metadata["title"] as? String
                val artist = metadata["artist"] as? String
                if (title != null || artist != null) {
                    _metadataInfo.value = MediaMetadataInfo(
                        title = title,
                        artist = artist
                    )
                }
            }

            mediaPlayer = player
            player.play()
        } catch (e: Exception) {
            _errorEvents.tryEmit("加载媒体失败: ${e.message}")
            _playbackState.value = AppPlaybackState.ERROR
        }
    }

    override suspend fun play() {
        mediaPlayer?.play()
    }

    override suspend fun pause() {
        mediaPlayer?.pause()
    }

    override suspend fun skipToNext() {
        if (currentPlaylist.isNotEmpty() && currentIndex < currentPlaylist.size - 1) {
            currentIndex++
            loadAndPlay(currentPlaylist[currentIndex])
        }
    }

    override suspend fun skipToPrevious() {
        if (currentPlaylist.isNotEmpty() && currentIndex > 0) {
            currentIndex--
            loadAndPlay(currentPlaylist[currentIndex])
        }
    }

    override suspend fun stop() {
        mediaPlayer?.stop()
        _isPlaying.value = false
        _playbackState.value = AppPlaybackState.IDLE
    }

    override fun release() {
        releaseCurrentPlayer()
        scope.cancel()
    }

    private fun releaseCurrentPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.dispose()
        mediaPlayer = null
    }
}
```

---

### Phase 4: 数据层 — 无数据库的 Repository 实现

**目标**：为每个 feature 创建不依赖 Room 的 Repository 实现，直接使用 `Pager` + `InMemoryPagingSource`。

#### Step 4.1: Domestic Repository — JVM 版

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/domestic/data/repository/DomesticRepositoryJvm.kt`

```kotlin
package com.example.kmp_demo.features.domestic.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.kmp_demo.core.data.paging.InMemoryPagingSource
import com.example.kmp_demo.core.videosource.domain.VideoSource
import com.example.kmp_demo.features.domestic.data.remote.DomesticApi
import com.example.kmp_demo.features.domestic.data.remote.DomesticSearchEngine
import com.example.kmp_demo.features.domestic.data.remote.mapper.toDomesticMedia
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import com.example.kmp_demo.features.domestic.domain.repository.DomesticRepository
import kotlinx.coroutines.flow.Flow

/**
 * Desktop 版 DomesticRepository — 无 Room 缓存。
 *
 * 直接使用 [Pager] + [InMemoryPagingSource] 从网络加载分页数据。
 * 每次启动都是全新会话，不持久化任何数据。
 */
class DomesticRepositoryJvm(
    private val domesticApi: DomesticApi,
    private val searchEngine: DomesticSearchEngine,
) : DomesticRepository {

    override suspend fun getAvailableTypes(): List<String> {
        return domesticApi.discoverTypes()
    }

    override fun getRecentMediaPaging(typeName: String): Flow<PagingData<DomesticMedia>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = {
                InMemoryPagingSource { page, pageSize ->
                    val typeParam = if (typeName == "全部") null else typeName
                    val items = domesticApi.getRecentMedia(page = page, typeName = typeParam)
                    items.map { it.toDomesticMedia() }
                }
            }
        ).flow
    }

    override suspend fun search(keyword: String, page: Int): List<DomesticMedia> {
        val apiResults = domesticApi.search(keyword, page)
        if (apiResults.isNotEmpty()) {
            return apiResults.map { it.toDomesticMedia() }
        }
        return searchEngine.search(keyword)
    }

    override suspend fun getDetailMeta(title: String): Result<DomesticMedia> {
        return try {
            val apiItem = domesticApi.searchFirstMatch(title)
            if (apiItem != null) {
                Result.success(apiItem.toDomesticMedia())
            } else {
                Result.success(
                    DomesticMedia(
                        id = title.hashCode().toUInt().toString(16),
                        title = title,
                        coverUrl = null,
                        year = null,
                        area = null,
                        type = com.example.kmp_demo.features.domestic.domain.model.DomesticMediaType.DRAMA,
                        description = null,
                        remarks = null,
                        videoSources = emptyList(),
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDetailSources(title: String): List<VideoSource> {
        return try {
            searchEngine.search(title).flatMap { it.videoSources }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
```

#### Step 4.2: Film Repository — JVM 版

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/film/data/repository/FilmRepositoryJvm.kt`

```kotlin
package com.example.kmp_demo.features.film.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.kmp_demo.core.data.paging.InMemoryPagingSource
import com.example.kmp_demo.features.film.data.remote.FilmApi
import com.example.kmp_demo.features.film.data.remote.SnifferDataSource
import com.example.kmp_demo.features.film.data.remote.dto.GenreDto
import com.example.kmp_demo.features.film.data.remote.mapper.toMovie
import com.example.kmp_demo.features.film.data.remote.mapper.toMovieDetail
import com.example.kmp_demo.features.film.domain.model.Movie
import com.example.kmp_demo.features.film.domain.model.MovieDetail
import com.example.kmp_demo.features.film.domain.model.MovieSortOrder
import com.example.kmp_demo.features.film.domain.model.VideoSource
import com.example.kmp_demo.features.film.domain.repository.FilmRepository
import kotlinx.coroutines.flow.Flow

/**
 * Desktop 版 FilmRepository — 无 Room 缓存。
 *
 * 热门/分类浏览使用 [Pager] + [InMemoryPagingSource]，
 * 搜索使用 [FilmPagingSource]（它本身不依赖 Room）。
 */
class FilmRepositoryJvm(
    private val api: FilmApi,
    private val snifferDataSource: SnifferDataSource,
) : FilmRepository {

    override fun getPopularMovies(): Flow<PagingData<Movie>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = {
                InMemoryPagingSource { page, pageSize ->
                    val response = api.getPopularMovies(page = page)
                    response.results.map { it.toMovie() }
                }
            }
        ).flow
    }

    override fun searchMovies(query: String): Flow<PagingData<Movie>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = {
                InMemoryPagingSource { page, pageSize ->
                    val response = api.searchMovies(query = query, page = page)
                    response.results.map { it.toMovie() }
                }
            }
        ).flow
    }

    override fun getMoviesByGenre(
        genreId: String,
        sortOrder: MovieSortOrder
    ): Flow<PagingData<Movie>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = {
                InMemoryPagingSource { page, pageSize ->
                    val response = api.getMoviesByGenre(
                        genreId = genreId,
                        page = page,
                        sortBy = sortOrder.value
                    )
                    response.results.map { it.toMovie() }
                }
            }
        ).flow
    }

    override suspend fun getMovieDetail(movieId: Int): Result<MovieDetail> {
        return try {
            val response = api.getMovieDetail(movieId)
            Result.success(response.toMovieDetail())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMovieGenres(): Result<List<GenreDto>> {
        return try {
            val response = api.getMovieGenres()
            Result.success(response.genres)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchVideoSources(title: String): List<VideoSource> {
        return snifferDataSource.searchSources(title)
    }
}
```

#### Step 4.3: Radio Repository — JVM 版

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/radio/data/repository/RadioRepositoryJvm.kt`

```kotlin
package com.example.kmp_demo.features.radio.data.repository

import com.example.kmp_demo.features.radio.data.remote.IpApiService
import com.example.kmp_demo.features.radio.data.remote.RadioApiService
import com.example.kmp_demo.features.radio.data.remote.mapper.toDomain
import com.example.kmp_demo.features.radio.domain.model.RadioStation
import com.example.kmp_demo.features.radio.domain.repository.RadioRepository

/**
 * Desktop 版 RadioRepository — 无 Room 缓存。
 *
 * 电台数据直接从网络获取，不持久化。
 * 收藏功能使用内存存储（或后续可改用 Preferences）。
 */
class RadioRepositoryJvm(
    private val radioApi: RadioApiService,
    private val ipApi: IpApiService,
) : RadioRepository {

    private val favoriteIds = mutableSetOf<String>()

    override suspend fun getStationsByCountry(countryCode: String): List<RadioStation> {
        return radioApi.getStationsByCountry(countryCode).map { it.toDomain() }
    }

    override suspend fun searchStations(query: String): List<RadioStation> {
        return radioApi.searchStations(query).map { it.toDomain() }
    }

    override suspend fun getStationsByLanguage(language: String): List<RadioStation> {
        return radioApi.getStationsByLanguage(language).map { it.toDomain() }
    }

    override suspend fun getPopularStations(): List<RadioStation> {
        return radioApi.getPopularStations().map { it.toDomain() }
    }

    override suspend fun getUserCountry(): String {
        return ipApi.getUserCountry()
    }

    override suspend fun addFavorite(station: RadioStation) {
        favoriteIds.add(station.id)
    }

    override suspend fun removeFavorite(stationId: String) {
        favoriteIds.remove(stationId)
    }

    override suspend fun getFavorites(): List<RadioStation> {
        return emptyList()
    }

    override fun isFavorite(stationId: String): Boolean {
        return stationId in favoriteIds
    }
}
```

---

### Phase 5: DI 模块 — Desktop 专属

**目标**：创建 Desktop 端的 DI 模块，覆盖 commonMain 中的 Room 依赖模块。

#### Step 5.1: 创建 Desktop 版 Domestic DI 模块

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/domestic/di/DomesticModule.jvm.kt`

```kotlin
package com.example.kmp_demo.features.domestic.di

import com.example.kmp_demo.core.videosource.VideoSourceSearchEngine
import com.example.kmp_demo.core.videosource.VideoSourceSiteConfigProvider
import com.example.kmp_demo.core.videosource.VideoSourceSiteLoader
import com.example.kmp_demo.features.domestic.data.remote.DomesticApi
import com.example.kmp_demo.features.domestic.data.remote.DomesticSearchEngine
import com.example.kmp_demo.features.domestic.data.repository.DomesticRepositoryJvm
import com.example.kmp_demo.features.domestic.domain.repository.DomesticRepository
import com.example.kmp_demo.features.domestic.ui.DomesticDetailViewModel
import com.example.kmp_demo.features.domestic.ui.DomesticSearchViewModel
import com.example.kmp_demo.features.domestic.ui.DomesticViewModel
import io.ktor.client.HttpClient
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val domesticModuleJvm = module {
    single { DomesticApi(get<HttpClient>(), get<VideoSourceSiteLoader>(), get<VideoSourceSiteConfigProvider>()) }
    single { DomesticSearchEngine(get<VideoSourceSearchEngine>()) }
    single<DomesticRepository> { DomesticRepositoryJvm(get(), get()) }
    viewModelOf(::DomesticViewModel)
    viewModelOf(::DomesticSearchViewModel)
    viewModelOf(::DomesticDetailViewModel)
}
```

#### Step 5.2: 创建 Desktop 版 Film DI 模块

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/film/di/FilmModule.jvm.kt`

```kotlin
package com.example.kmp_demo.features.film.di

import com.example.kmp_demo.features.film.data.remote.FilmApi
import com.example.kmp_demo.features.film.data.remote.SnifferDataSource
import com.example.kmp_demo.features.film.data.repository.FilmRepositoryJvm
import com.example.kmp_demo.features.film.domain.repository.FilmRepository
import com.example.kmp_demo.features.film.ui.FilmDetailViewModel
import com.example.kmp_demo.features.film.ui.FilmViewModel
import com.example.kmp_demo.features.film.ui.search.FilmSearchViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val filmModuleJvm = module {
    single { FilmApi(get()) }
    single { SnifferDataSource(get()) }
    single<FilmRepository> { FilmRepositoryJvm(get(), get()) }
    viewModelOf(::FilmViewModel)
    viewModelOf(::FilmDetailViewModel)
    viewModelOf(::FilmSearchViewModel)
}
```

#### Step 5.3: 创建 Desktop 版 Radio DI 模块

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/radio/di/RadioModule.jvm.kt`

```kotlin
package com.example.kmp_demo.features.radio.di

import com.example.kmp_demo.features.radio.data.remote.IpApiService
import com.example.kmp_demo.features.radio.data.remote.RadioApiService
import com.example.kmp_demo.features.radio.data.repository.RadioRepositoryJvm
import com.example.kmp_demo.features.radio.domain.repository.RadioRepository
import com.example.kmp_demo.features.radio.player.RadioPlayerManager
import com.example.kmp_demo.features.radio.ui.list.RadioListViewModel
import com.example.kmp_demo.features.radio.ui.search.RadioSearchViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val radioModuleJvm = module {
    factory { RadioApiService(get()) }
    factory { IpApiService(get()) }
    single<RadioRepository> { RadioRepositoryJvm(get(), get()) }
    single { RadioPlayerManager(get()) }
    viewModelOf(::RadioListViewModel)
    viewModelOf(::RadioSearchViewModel)
}
```

---

### Phase 6: Desktop UI — 独立页面实现

**目标**：为 Desktop 端创建独立的 UI 页面，适配宽屏和鼠标交互。

#### Step 6.1: Desktop 布局框架

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/App.jvm.kt`（重写）

Desktop 布局采用 **左侧导航栏 + 右侧内容区** 的经典桌面布局：

```
┌─────────────────────────────────────────────────┐
│  ┌──────┐  ┌──────────────────────────────────┐ │
│  │      │  │                                  │ │
│  │  📻  │  │                                  │ │
│  │  电台 │  │                                  │ │
│  │      │  │         内容区                    │ │
│  │  🎬  │  │    (NavHost 路由切换)             │ │
│  │  电影 │  │                                  │ │
│  │      │  │                                  │ │
│  │  📺  │  │                                  │ │
│  │  国产 │  │                                  │ │
│  │      │  │                                  │ │
│  └──────┘  └──────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

#### Step 6.2: Desktop 版 Domestic UI

**文件清单**（均在 `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/domestic/ui/` 下）：

| 文件 | 说明 |
|------|------|
| `DomesticHomeScreen.kt` | 首页：宽屏网格（3-4列），顶部搜索栏 + 分类筛选 |
| `DomesticDetailScreen.kt` | 详情页：左侧封面+简介，右侧播放源列表 |
| `DomesticSearchScreen.kt` | 搜索页：搜索框 + 结果列表 |
| `components/DomesticMediaCard.kt` | 卡片组件：大封面 + hover 效果 |

#### Step 6.3: Desktop 版 Film UI

**文件清单**（均在 `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/film/ui/` 下）：

| 文件 | 说明 |
|------|------|
| `FilmHomeScreen.kt` | 首页：宽屏网格 + 分类/排序筛选 |
| `FilmDetailScreen.kt` | 详情页：分栏布局 |
| `FilmSearchScreen.kt` | 搜索页 |
| `components/MovieCard.kt` | 卡片组件 |

#### Step 6.4: Desktop 版 Radio UI

**文件清单**（均在 `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/radio/ui/` 下）：

| 文件 | 说明 |
|------|------|
| `RadioListScreen.kt` | 列表页：左侧国家列表 + 右侧电台网格 |
| `RadioSearchScreen.kt` | 搜索页 |
| `components/RadioStationCard.kt` | 电台卡片 |
| `components/MiniPlayerBar.kt` | 底部迷你播放栏 |

#### Step 6.5: Desktop 导航图

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/domestic/DomesticNavigation.jvm.kt`
**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/film/FilmNavigation.jvm.kt`
**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/features/radio/RadioNavigation.jvm.kt`

---

### Phase 7: Desktop 入口更新

**目标**：更新 `desktopApp` 入口，使用新的 DI 模块和 UI。

#### Step 7.1: 更新 `main.kt`

**文件**: `desktopApp/src/main/kotlin/com/example/kmp_demo/main.kt`

```kotlin
package com.example.kmp_demo

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.example.kmp_demo.core.videosource.di.coreVideosourceModule
import com.example.kmp_demo.di.commonModule
import com.example.kmp_demo.di.platformModule
import com.example.kmp_demo.features.domestic.di.domesticModuleJvm
import com.example.kmp_demo.features.film.di.filmModuleJvm
import com.example.kmp_demo.features.radio.di.radioModuleJvm
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

fun main() = application {
    startKoin {
        printLogger(Level.INFO)
        modules(
            commonModule,
            platformModule,          // 已移除 Room，只保留播放器
            coreVideosourceModule,
            radioModuleJvm,          // ✅ 使用 JVM 版（无数据库）
            filmModuleJvm,           // ✅ 使用 JVM 版（无数据库）
            domesticModuleJvm,       // ✅ 使用 JVM 版（无数据库）
        )
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "CineWave",
        state = WindowState(
            size = DpSize(1280.dp, 800.dp),
            position = WindowPosition(Alignment.Center)
        )
    ) {
        App()  // 使用 jvmMain 中的 App.jvm.kt
    }
}
```

#### Step 7.2: 更新 `desktopApp/build.gradle.kts`

```kotlin
dependencies {
    // JavaFX 依赖（视频和电台播放器）
    implementation("org.openjfx:javafx-media:21")
    implementation("org.openjfx:javafx-swing:21")
    
    // 其他现有依赖...
}
```

---

## 六、文件变更清单

### 6.1 新增文件

| # | 文件路径 | 说明 |
|---|---------|------|
| 1 | `shared/src/jvmMain/.../core/data/paging/InMemoryPagingSource.kt` | 内存分页数据源 |
| 2 | `shared/src/jvmMain/.../features/domestic/data/repository/DomesticRepositoryJvm.kt` | Domestic 无数据库 Repository |
| 3 | `shared/src/jvmMain/.../features/film/data/repository/FilmRepositoryJvm.kt` | Film 无数据库 Repository |
| 4 | `shared/src/jvmMain/.../features/radio/data/repository/RadioRepositoryJvm.kt` | Radio 无数据库 Repository |
| 5 | `shared/src/jvmMain/.../features/domestic/di/DomesticModule.jvm.kt` | Domestic JVM DI 模块 |
| 6 | `shared/src/jvmMain/.../features/film/di/FilmModule.jvm.kt` | Film JVM DI 模块 |
| 7 | `shared/src/jvmMain/.../features/radio/di/RadioModule.jvm.kt` | Radio JVM DI 模块 |
| 8 | `shared/src/jvmMain/.../features/domestic/ui/DomesticHomeScreen.kt` | Desktop 国内影视首页 |
| 9 | `shared/src/jvmMain/.../features/domestic/ui/DomesticDetailScreen.kt` | Desktop 国内影视详情页 |
| 10 | `shared/src/jvmMain/.../features/domestic/ui/DomesticSearchScreen.kt` | Desktop 国内影视搜索页 |
| 11 | `shared/src/jvmMain/.../features/domestic/ui/components/DomesticMediaCard.kt` | Desktop 国内影视卡片 |
| 12 | `shared/src/jvmMain/.../features/film/ui/FilmHomeScreen.kt` | Desktop 电影首页 |
| 13 | `shared/src/jvmMain/.../features/film/ui/FilmDetailScreen.kt` | Desktop 电影详情页 |
| 14 | `shared/src/jvmMain/.../features/film/ui/FilmSearchScreen.kt` | Desktop 电影搜索页 |
| 15 | `shared/src/jvmMain/.../features/film/ui/components/MovieCard.kt` | Desktop 电影卡片 |
| 16 | `shared/src/jvmMain/.../features/radio/ui/RadioListScreen.kt` | Desktop 电台列表页 |
| 17 | `shared/src/jvmMain/.../features/radio/ui/RadioSearchScreen.kt` | Desktop 电台搜索页 |
| 18 | `shared/src/jvmMain/.../features/radio/ui/components/RadioStationCard.kt` | Desktop 电台卡片 |
| 19 | `shared/src/jvmMain/.../features/radio/ui/components/MiniPlayerBar.kt` | Desktop 迷你播放栏 |
| 20 | `shared/src/jvmMain/.../features/domestic/DomesticNavigation.jvm.kt` | Desktop 国内影视导航图 |
| 21 | `shared/src/jvmMain/.../features/film/FilmNavigation.jvm.kt` | Desktop 电影导航图 |
| 22 | `shared/src/jvmMain/.../features/radio/RadioNavigation.jvm.kt` | Desktop 电台导航图 |

### 6.2 修改文件

| # | 文件路径 | 变更说明 |
|---|---------|---------|
| 1 | `shared/src/jvmMain/.../App.jvm.kt` | 重写：左侧导航 + NavHost + Desktop UI |
| 2 | `shared/src/jvmMain/.../di/PlatformModule.jvm.kt` | 移除 Room Database 注册 |
| 3 | `shared/src/jvmMain/.../core/player/platform/DesktopVideoPlayerController.kt` | 重写：基于 JavaFX MediaPlayer |
| 4 | `shared/src/jvmMain/.../core/player/ui/PlatformVideoPlayerScreen.jvm.kt` | 重写：SwingPanel + MediaView |
| 5 | `shared/src/jvmMain/.../features/radio/player/DesktopRadioPlayerController.kt` | 重写：基于 JavaFX MediaPlayer |
| 6 | `desktopApp/src/main/kotlin/.../main.kt` | 使用 JVM 版 DI 模块 |
| 7 | `desktopApp/build.gradle.kts` | 添加 JavaFX 依赖 |

### 6.3 可删除文件

| # | 文件路径 | 说明 |
|---|---------|------|
| 1 | `shared/src/jvmMain/.../core/data/local/room/AppDatabase.jvm.kt` | Desktop 不再需要 Room，可删除 |

---

## 七、对 Android 端的影响评估

### 7.1 零影响

| 组件 | 影响 | 原因 |
|------|------|------|
| `commonMain` 所有代码 | ✅ 无影响 | 未修改任何 commonMain 文件 |
| `androidMain` 所有代码 | ✅ 无影响 | 未修改任何 androidMain 文件 |
| Android DI 模块 | ✅ 无影响 | `domesticModule`、`filmModule`、`radioModule` 继续使用 Room 版本 |
| Android 入口 | ✅ 无影响 | `App.android.kt` 和 `MainActivity.kt` 不变 |
| Room 数据库 | ✅ 无影响 | Android 端继续使用 Room + RemoteMediator |
| ExoPlayer 播放器 | ✅ 无影响 | Android 端继续使用 ExoPlayer |
| ViewModel | ✅ 无影响 | 复用 commonMain 的 ViewModel，接口不变 |

### 7.2 隔离机制

```
commonMain (Android + Desktop 共用)
├── DomesticRepository (接口) ← Android 用 DomesticRepositoryImpl (Room)
│                              ← Desktop 用 DomesticRepositoryJvm (内存)
├── FilmRepository (接口)     ← Android 用 FilmRepositoryImpl (Room)
│                              ← Desktop 用 FilmRepositoryJvm (内存)
├── RadioRepository (接口)    ← Android 用 RadioRepositoryImpl (Room)
│                              ← Desktop 用 RadioRepositoryJvm (内存)
├── IPlayerController (视频)  ← Android 用 ExoPlayerController
│                              ← Desktop 用 DesktopVideoPlayerController (JavaFX)
└── IPlayerController (电台)  ← Android 用 ExoPlayer/Media3
                               ← Desktop 用 DesktopRadioPlayerController (JavaFX)
```

通过 Koin DI 的模块隔离，Android 和 Desktop 各自注册不同的实现，互不干扰。

---

## 八、实施路线图

### Phase 1: 基础设施（预估 0.5 天）

| 任务 | 文件 | 说明 |
|------|------|------|
| 1.1 创建 InMemoryPagingSource | `core/data/paging/InMemoryPagingSource.kt` | 内存分页数据源 |
| 1.2 更新 PlatformModule.jvm.kt | `di/PlatformModule.jvm.kt` | 移除 Room 注册 |
| 1.3 删除 AppDatabase.jvm.kt | `core/data/local/room/AppDatabase.jvm.kt` | 不再需要 |

### Phase 2: 视频播放器（预估 2 天）

| 任务 | 文件 | 说明 |
|------|------|------|
| 2.1 重写 DesktopVideoPlayerController | `core/player/platform/` | JavaFX MediaPlayer 封装 |
| 2.2 重写 PlatformVideoPlayerScreen.jvm.kt | `core/player/ui/` | SwingPanel + MediaView |
| 2.3 更新 desktopApp/build.gradle.kts | `desktopApp/` | 添加 JavaFX 依赖 |

### Phase 3: 电台播放器（预估 1 天）

| 任务 | 文件 | 说明 |
|------|------|------|
| 3.1 重写 DesktopRadioPlayerController | `features/radio/player/` | JavaFX MediaPlayer 封装 |

### Phase 4: 数据层（预估 1 天）

| 任务 | 文件 | 说明 |
|------|------|------|
| 4.1 创建 DomesticRepositoryJvm | `features/domestic/data/repository/` | 无数据库 Repository |
| 4.2 创建 FilmRepositoryJvm | `features/film/data/repository/` | 无数据库 Repository |
| 4.3 创建 RadioRepositoryJvm | `features/radio/data/repository/` | 无数据库 Repository |

### Phase 5: DI 模块（预估 0.5 天）

| 任务 | 文件 | 说明 |
|------|------|------|
| 5.1 创建 DomesticModule.jvm.kt | `features/domestic/di/` | JVM DI 模块 |
| 5.2 创建 FilmModule.jvm.kt | `features/film/di/` | JVM DI 模块 |
| 5.3 创建 RadioModule.jvm.kt | `features/radio/di/` | JVM DI 模块 |
| 5.4 更新 main.kt | `desktopApp/` | 使用 JVM 版模块 |

### Phase 6: Desktop UI（预估 3-4 天）

| 任务 | 文件 | 说明 |
|------|------|------|
| 6.1 重写 App.jvm.kt | `App.jvm.kt` | 左侧导航 + NavHost |
| 6.2 创建 Domestic UI 页面 | `features/domestic/ui/` | 3 个页面 + 1 个组件 |
| 6.3 创建 Film UI 页面 | `features/film/ui/` | 3 个页面 + 1 个组件 |
| 6.4 创建 Radio UI 页面 | `features/radio/ui/` | 2 个页面 + 2 个组件 |
| 6.5 创建 Desktop 导航图 | `features/*/Navigation.jvm.kt` | 3 个导航图 |

### Phase 7: 集成测试（预估 1 天）

| 任务 | 说明 |
|------|------|
| 7.1 编译 Desktop 应用 | `./gradlew :desktopApp:compileKotlin` |
| 7.2 运行 Desktop 应用 | `./gradlew :desktopApp:run` |
| 7.3 测试视频播放 | MP4/HLS 视频播放、进度控制、音量控制 |
| 7.4 测试电台播放 | MP3/AAC 流媒体播放、播放列表切换 |
| 7.5 测试所有页面切换 | 电台/电影/国内影视首页、详情、搜索 |
|
