# CineWave KMP Desktop 运行改造方案

> 作者：资深 KMP 架构师
> 日期：2026-05-22
> 项目：CineWave（KMP Compose Multiplatform）

---

## 一、现状分析

当前项目在 **Android** 上运行成功，但 **Desktop (JVM)** 目标虽然已在 `shared/build.gradle.kts` 中声明 `jvm()`，实际运行时存在大量平台特定代码阻塞。

### 1.1 项目架构概览

```
CineWave/
├── androidApp/          # Android 应用入口 + Android 特有代码
│   └── src/main/kotlin/
│       ├── MainActivity.kt
│       ├── MainApplication.kt      # Koin 初始化入口
│       ├── di/AndroidAppModule.kt  # Android DI 模块（Media3PlayerController）
│       └── features/radio/player/
│           └── Media3PlayerController.kt  # Android 电台播放器（Media3）
├── desktopApp/          # Desktop 应用入口（已存在但无法运行）
│   └── src/main/kotlin/
│       └── main.kt                  # 简单 Window { App() }，无 Koin 初始化
├── shared/              # 共享代码
│   ├── src/commonMain/  # 跨平台公共代码
│   │   ├── App.kt                  # 主 Composable
│   │   ├── di/AppModule.kt         # 通用 DI 模块
│   │   ├── di/PlatformModule.kt    # expect 平台模块
│   │   └── core/player/            # 视频播放器（依赖 compose-media-player）
│   ├── src/androidMain/ # Android 平台实现
│   │   ├── di/PlatformModule.android.kt  # actual 平台模块
│   │   └── core/player/platform/
│   │       └── ExoPlayerController.kt    # Android 视频播放器（ExoPlayer）
│   ├── src/jvmMain/     # JVM 平台实现（几乎为空）
│   │   └── Platform.jvm.kt          # 仅有 getPlatform() 实现
│   └── src/iosMain/     # iOS 平台实现
└── gradle/libs.versions.toml        # 版本目录
```

### 1.2 阻塞 Desktop 运行的核心问题

| # | 问题 | 严重程度 | 说明 |
|---|------|---------|------|
| 1 | **compose-media-player 库无 Desktop 支持** | 🔴 阻塞 | `VideoPlayerScreen.kt` 直接使用该库的 `VideoPlayerSurface`、`rememberVideoPlayerState`、`AutoPipEffect` |
| 2 | **Media3PlayerController 在 androidApp 中** | 🔴 阻塞 | 电台播放器使用 Android Media3 API，完全无法在 Desktop 使用 |
| 3 | **Koin 未在 Desktop 入口初始化** | 🔴 阻塞 | `main.kt` 中直接调用 `App()`，未调用 `startKoin` |
| 4 | **Android 特有 API 散布在 commonMain** | 🟡 高 | `LocalContext`、`LocalView`、`BackHandler`、`WindowInsets`、`keepScreenOn` |
| 5 | **Room 数据库 JVM 依赖缺失** | 🟡 高 | Room KMP 在 JVM 上需要 SQLite JDBC 驱动 |
| 6 | **jvmMain 目录几乎为空** | 🟡 高 | 缺少 `platformModule` 的 `actual` 实现、播放器实现、缓存目录等 |
| 7 | **Android 前台服务不可用** | 🟡 高 | `RadioPlaybackService` 和 `VideoPlaybackService` 是 Android Foreground Service，Desktop 上需要替换为本地播放 |
| 8 | **AndroidFullscreenController 在 androidApp 中** | 🟡 高 | 全屏控制使用 Android Activity API，Desktop 上需要使用 Compose Desktop 的 `WindowState.isFullscreen` |
| 9 | **`statusBarsPadding()` / `navigationBarsPadding()` 在 Desktop 上行为不同** | 🟢 低 | `Modifier_Ext.kt` 中的 `safeContent()` 在 Desktop 上可能返回 0 padding，不影响功能 |
| 10 | **`coreVideosourceModule` 未在 Desktop 入口注册** | 🔴 阻塞 | `MainApplication.kt` 中注册了 `coreVideosourceModule`，但 `main.kt` 中未包含 |
| 11 | **`androidAppModule` 在 Desktop 上不可用** | 🔴 阻塞 | `androidAppModule` 注册了 `Media3PlayerController`（Radio 播放器），Desktop 上需要替换为 `DesktopRadioPlayerController` |
| 12 | **`PlatformContext.getPlatformCachePath()` 缺少 JVM actual** | 🟡 高 | `PlatformUtils.kt` 中的 `expect fun PlatformContext.getPlatformCachePath()` 在 jvmMain 中没有 actual 实现 |
| 13 | **`showToast()` / `openAccessibilitySettings()` 缺少 JVM actual** | 🟢 低 | 这两个 expect 函数在 jvmMain 中没有 actual 实现，但 Desktop 上可以给空实现 |

---

## 二、改造方案

### 2.1 总体架构设计

```
shared/src/
├── commonMain/                    # 保持现有代码不变
│   └── core/player/
│       ├── domain/IPlayerController.kt    # 视频播放器接口（保留）
│       ├── platform/ComposeMediaPlayerController.kt  # 仅 Android/iOS 使用
│       └── ui/VideoPlayerScreen.kt       # 需要抽取平台相关部分
│
├── androidMain/                   # 保持现有代码不变
│   └── core/player/platform/
│       └── ExoPlayerController.kt        # Android 视频播放器
│
├── jvmMain/                       # 🆕 新增 Desktop 实现
│   ├── kotlin/com/example/kmp_demo/
│   │   ├── Platform.jvm.kt               # 已有
│   │   ├── di/
│   │   │   └── PlatformModule.jvm.kt     # 🆕 Desktop DI 模块
│   │   ├── core/
│   │   │   ├── player/
│   │   │   │   ├── platform/
│   │   │   │   │   └── DesktopVideoPlayerController.kt  # 🆕 Desktop 视频播放器
│   │   │   │   ├── cache/
│   │   │   │   │   └── PlatformCacheDir.jvm.kt          # 🆕 Desktop 缓存目录
│   │   │   │   └── ui/
│   │   │   │       └── PlatformVideoPlayerScreen.jvm.kt # 🆕 Desktop 视频播放器 UI
│   │   │   └── PlatformUtils.jvm.kt     # 🆕 Desktop 平台工具
│   │   └── features/radio/player/
│   │       └── DesktopRadioPlayerController.kt  # 🆕 Desktop 电台播放器
│   └── composeResources/          # 🆕 Desktop 资源（如有需要）
```

### 2.2 详细改造步骤

---

#### Step 1: 更新 `shared/build.gradle.kts` — 添加 jvmMain 依赖

```kotlin
// 在 shared/build.gradle.kts 的 sourceSets 中添加：
jvmMain.dependencies {
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.ktor.client.okhttp)  // 或 ktor-client-java
    implementation(libs.room.runtime)
    implementation(libs.kotlinx.coroutinesSwing)
    
    // JavaFX Media（用于 Desktop 音视频播放）
    implementation("org.openjfx:javafx-media:21")
    implementation("org.openjfx:javafx-swing:21")
}
```

---

#### Step 2: 创建 Desktop DI 模块

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/di/PlatformModule.jvm.kt`

```kotlin
package com.example.kmp_demo.di

import com.example.kmp_demo.core.player.cache.DiskLruCache
import com.example.kmp_demo.core.player.cache.M3u8CacheInterceptor
import com.example.kmp_demo.core.player.domain.IPlayerController as VideoPlayerController
import com.example.kmp_demo.core.player.platform.DesktopVideoPlayerController
import com.example.kmp_demo.features.radio.domain.player.IPlayerController as RadioPlayerController
import com.example.kmp_demo.features.radio.player.DesktopRadioPlayerController
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // === Room Database (JVM 使用 SQLite) ===
    single<AppDatabase> {
        getRoomDatabase(getDatabaseBuilder())
    }

    // === Disk Cache ===
    single<DiskLruCache> {
        val cacheDir = System.getProperty("user.home") + "/.cinewave/video_cache"
        DiskLruCache(cacheDir = cacheDir)
    }

    // === M3U8 Cache Interceptor ===
    single<M3u8CacheInterceptor> {
        val cacheDir = System.getProperty("user.home") + "/.cinewave/video_cache"
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

---

#### Step 3: 创建 Desktop 电台播放器

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
 * 注意：JavaFX MediaPlayer 需要在 JavaFX 线程上创建和操作。
 * 在 Compose Desktop 中，JavaFX 线程与 AWT 线程不同，
 * 需要通过 Platform.runLater() 或 JavaFX 应用启动器来桥接。
 * 
 * 简化方案：使用 javax.sound 或纯 Java 的 AudioSystem 播放 MP3/AAC。
 * 更推荐：使用 https://github.com/dheid/jlayer (MP3) 或 https://github.com/umjammer/vavi-media
 */
class DesktopRadioPlayerController : IPlayerController {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlaylist = listOf<PlayableMedia>()
    private var currentIndex = -1

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
            mediaPlayer = MediaPlayer(media).apply {
                setOnReady {
                    _playbackState.value = AppPlaybackState.READY
                    play()
                }
                setOnPlaying {
                    _isPlaying.value = true
                    _playbackState.value = AppPlaybackState.READY
                }
                setOnPaused {
                    _isPlaying.value = false
                }
                setOnError {
                    _errorEvents.tryEmit(errorMessage ?: "未知播放错误")
                    _playbackState.value = AppPlaybackState.ERROR
                }
                setOnEndOfMedia {
                    // 电台流通常不会结束，但如果是播放列表则自动下一首
                    skipToNext()
                }
            }
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
        mediaPlayer?.dispose()
        mediaPlayer = null
    }
}
```

---

#### Step 4: 创建 Desktop 视频播放器控制器

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
 * 支持格式：MP4, HLS (有限支持), 本地文件
 * 注意：JavaFX 需要在模块路径或 JDK 中包含 javafx.media
 */
class DesktopVideoPlayerController(
    private val diskCache: DiskLruCache? = null
) : IPlayerController {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var positionPollingJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

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
        releaseCurrentPlayer()

        try {
            val media = Media(url)
            mediaPlayer = MediaPlayer(media).apply {
                volume = _volume.value

                setOnReady {
                    _duration.value = (totalDuration.toLong() * 1000)
                    _playbackState.value = VideoPlaybackState.READY
                    startPositionPolling()
                }
                setOnPlaying {
                    _playbackState.value = VideoPlaybackState.PLAYING
                }
                setOnPaused {
                    _playbackState.value = VideoPlaybackState.PAUSED
                }
                setOnEndOfMedia {
                    _playbackState.value = VideoPlaybackState.ENDED
                }
                setOnError {
                    _playbackState.value = VideoPlaybackState.ERROR
                }
                play()
            }
        } catch (e: Exception) {
            _playbackState.value = VideoPlaybackState.ERROR
        }
    }

    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    _currentPosition.value = (mp.currentTime.toMillis())
                    if (mp.totalDuration > 0) {
                        val dur = mp.totalDuration.toMillis()
                        _duration.value = dur
                        _bufferedPercent.value = 
                            ((mp.bufferProgressTime.toMillis().toFloat() / dur) * 100).toInt().coerceIn(0, 100)
                    }
                }
                delay(250)
            }
        }
    }

    override suspend fun play() { mediaPlayer?.play() }
    override suspend fun pause() { mediaPlayer?.pause() }

    override suspend fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.status == MediaPlayer.Status.PLAYING) it.pause()
            else it.play()
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        mediaPlayer?.seek(javafx.util.Duration.millis(positionMs.toDouble()))
    }

    override suspend fun seekForward(seconds: Long) {
        mediaPlayer?.let {
            val newPos = (it.currentTime.toMillis() + seconds * 1000)
                .coerceAtMost(it.totalDuration.toMillis())
            it.seek(javafx.util.Duration.millis(newPos))
        }
    }

    override suspend fun seekBackward(seconds: Long) {
        mediaPlayer?.let {
            val newPos = (it.currentTime.toMillis() - seconds * 1000).coerceAtLeast(0.0)
            it.seek(javafx.util.Duration.millis(newPos))
        }
    }

    override suspend fun setVolume(volume: Float) {
        mediaPlayer?.volume = volume.toDouble()
        _volume.value = volume
    }

    override suspend fun toggleFullScreen() {
        _isFullScreen.value = !_isFullScreen.value
    }

    override fun release() {
        positionPollingJob?.cancel()
        scope.cancel()
        releaseCurrentPlayer()
    }

    private fun releaseCurrentPlayer() {
        mediaPlayer?.dispose()
        mediaPlayer = null
    }
}
```

---

#### Step 5: 创建 Desktop 视频播放器 UI

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/core/player/ui/PlatformVideoPlayerScreen.jvm.kt`

```kotlin
package com.example.kmp_demo.core.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import com.example.kmp_demo.core.player.domain.VideoPlayerUiState

/**
 * Desktop 平台视频播放器屏幕
 *
 * 由于 JavaFX MediaPlayer 需要 JavaFX 线程，而 Compose Desktop 运行在 AWT 线程上，
 * 这里使用 SwingPanel 嵌入 JavaFX MediaView。
 * 
 * 简化方案：使用纯 Compose 实现，通过 Canvas 或第三方库渲染视频帧。
 * 更实用的方案：使用 https://github.com/openjfx/javafx-maven-plugin 集成 JavaFX。
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
    // Desktop 实现：使用 SwingPanel 嵌入 JavaFX MediaView
    // 或使用纯音频播放 + 占位 UI
    // 此处为简化实现，直接显示控制栏 + 黑色背景
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 视频渲染区域（使用 SwingPanel 嵌入 JavaFX MediaView）
        // 或使用 Compose Canvas 绘制视频帧
        
        // 控制栏
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            // controls(uiState) { ... }
        }
        
        // 顶栏
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            // topBar?.invoke(this)
        }
    }
}
```

---

#### Step 6: 创建 Desktop 缓存目录提供者

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/core/player/cache/PlatformCacheDir.jvm.kt`

```kotlin
package com.example.kmp_demo.core.player.platform

/**
 * Desktop 平台获取默认缓存目录
 */
fun getDefaultCacheDir(): String {
    return System.getProperty("user.home") + "/.cinewave"
}
```

---

#### Step 7: 更新 Desktop 入口 — 添加 Koin 初始化

**文件**: `desktopApp/src/main/kotlin/com/example/kmp_demo/main.kt`

```kotlin
package com.example.kmp_demo

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import com.example.kmp_demo.di.commonModule
import com.example.kmp_demo.di.platformModule
import com.example.kmp_demo.features.domestic.di.domesticModule
import com.example.kmp_demo.features.film.di.filmModule
import com.example.kmp_demo.features.radio.di.radioModule
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

fun main() = application {
    // 初始化 Koin（在 Composable 之前）
    startKoin {
        printLogger(Level.INFO)
        modules(
            commonModule,
            platformModule,
            radioModule,
            filmModule,
            domesticModule,
        )
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "CineWave",
        state = androidx.compose.ui.window.WindowState(
            size = DpSize(1280.dp, 800.dp),
            position = WindowPosition(Alignment.Center)
        )
    ) {
        App()
    }
}
```

---

#### Step 8: 处理 Android 特有 API 的跨平台兼容

**问题文件**: `shared/src/commonMain/kotlin/com/example/kmp_demo/core/player/ui/VideoPlayerScreen.kt`

该文件使用了以下 Android 特有 API，需要改造：

| API | 使用位置 | 改造方案 |
|-----|---------|---------|
| `LocalContext` | 获取 `getDefaultCacheDir` | 使用 `expect/actual` 提供跨平台缓存目录 |
| `LocalView` | `currentView.keepScreenOn` | Desktop 上忽略，使用 `expect/actual` |
| `BackHandler` | 返回键处理 | Desktop 上监听键盘 Escape 键 |
| `WindowInsets.statusBars` | 状态栏 padding | Desktop 上使用固定值 0.dp |
| `AutoPipEffect` | 画中画 | Desktop 上忽略 |

**解决方案**：将 `VideoPlayerScreen.kt` 中的平台相关部分抽取为 `expect` 函数：

```kotlin
// commonMain
expect fun isKeepScreenOnSupported(): Boolean
expect fun getPlatformWindowInsets(): WindowInsets
```

```kotlin
// jvmMain
actual fun isKeepScreenOnSupported(): Boolean = false
actual fun getPlatformWindowInsets(): WindowInsets = WindowInsets(0, 0, 0, 0)
```

---

#### Step 9: 创建 Desktop Room 数据库 actual 实现

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/core/data/local/room/AppDatabase.jvm.kt`

```kotlin
package com.example.kmp_demo.core.data.local.room

import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * JVM 平台上的 AppDatabaseConstructor — Room KSP 自动生成 actual 实现
 * 注意：开启 generateKotlin = true 后，Room KSP 会自动生成 AppDatabaseConstructor 的 actual 实现。
 * 如果手动编写 actual object，会导致 "The @ConstructedBy definition must be an 'expect' declaration" 编译错误。
 * 因此这里只需要提供 getDatabaseBuilder() 的 actual 实现。
 */

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder(
        name = "study_demo.db",  // JVM 版 Room 使用 name 参数而非 context
        klass = AppDatabase::class.java,
    )
}
```

---

#### Step 10: 创建 Desktop PlatformUtils（expect/actual 实现）

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/core/PlatformUtils.jvm.kt`

```kotlin
package com.example.kmp_demo.core

import coil3.PlatformContext
import okio.Path

/**
 * Desktop 平台特定工具函数实现
 */

actual fun showToast(message: String) {
    // Desktop 上使用 Swing 或系统通知替代
    println("Toast: $message")
}

actual fun openAccessibilitySettings() {
    // Desktop 上无需无障碍设置
}

actual fun PlatformContext.getPlatformCachePath(): String {
    return System.getProperty("user.home") + "/.cinewave"
}
```

---

#### Step 11: 创建 Desktop FullscreenController

**文件**: `shared/src/jvmMain/kotlin/com/example/kmp_demo/core/player/domain/DesktopFullscreenController.kt`

```kotlin
package com.example.kmp_demo.core.player.domain

import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState

/**
 * Desktop 全屏控制器 — 使用 Compose Desktop 的 WindowState
 *
 * 在 Desktop 入口（main.kt）中通过 CompositionLocalProvider 注入。
 */
class DesktopFullscreenController(
    private val windowState: WindowState
) : FullscreenController {

    override fun enterFullscreen() {
        windowState.isFullscreen = true
    }

    override fun exitFullscreen() {
        windowState.isFullscreen = false
    }
}
```

---

#### Step 12: 更新 Desktop 入口 — 添加 FullscreenController 和 coreVideosourceModule

**文件**: `desktopApp/src/main/kotlin/com/example/kmp_demo/main.kt`

```kotlin
package com.example.kmp_demo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.example.kmp_demo.core.player.domain.DesktopFullscreenController
import com.example.kmp_demo.core.player.domain.LocalFullscreenController
import com.example.kmp_demo.core.videosource.di.coreVideosourceModule
import com.example.kmp_demo.di.commonModule
import com.example.kmp_demo.di.platformModule
import com.example.kmp_demo.features.domestic.di.domesticModule
import com.example.kmp_demo.features.film.di.filmModule
import com.example.kmp_demo.features.radio.di.radioModule
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

fun main() = application {
    // 初始化 Koin（在 Composable 之前）
    startKoin {
        printLogger(Level.INFO)
        modules(
            commonModule,
            platformModule,
            coreVideosourceModule,  // 🆕 新增：视频源模块
            radioModule,
            filmModule,
            domesticModule,
        )
    }

    val windowState = WindowState(
        size = DpSize(1280.dp, 800.dp),
        position = WindowPosition(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "CineWave",
        state = windowState,
    ) {
        // 注入 Desktop 全屏控制器
        val fullscreenController = DesktopFullscreenController(windowState)
        CompositionLocalProvider(
            LocalFullscreenController provides fullscreenController
        ) {
            App()
        }
    }
}
```

---

#### Step 13: 更新 `desktopApp/build.gradle.kts`

```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.compose.uiToolingPreview)
    
    // Koin（不含 android）
    implementation(libs.koin.core)
    
    // JavaFX（如果需要）
    // implementation("org.openjfx:javafx-media:21")
}

compose.desktop {
    application {
        mainClass = "com.example.kmp_demo.MainKt"
        
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "CineWave"
            packageVersion = "1.0.0"
            
            // Windows 配置
            windows {
                menuGroup = "CineWave"
                upgradeUuid = "cinewave-desktop-app"
            }
            
            // macOS 配置
            macOS {
                bundleID = "com.example.kmp_demo"
            }
        }
    }
}
```

---

## 三、实施路线图

### Phase 1: 基础设施（预估 1-2 天）

| 任务 | 文件 | 说明 |
|------|------|------|
| 1.1 更新 `shared/build.gradle.kts` | `shared/build.gradle.kts` | 添加 jvmMain 依赖 |
| 1.2 创建 Desktop DI 模块 | `shared/src/jvmMain/.../di/PlatformModule.jvm.kt` | 提供所有 Desktop 依赖 |
| 1.3 更新 Desktop 入口 | `desktopApp/src/main/kotlin/.../main.kt` | 添加 Koin 初始化 |
| 1.4 更新 `desktopApp/build.gradle.kts` | `desktopApp/build.gradle.kts` | 添加 Koin 依赖 |

### Phase 2: 电台播放器（预估 1 天）

| 任务 | 文件 | 说明 |
|------|------|------|
| 2.1 创建 DesktopRadioPlayerController | `shared/src/jvmMain/.../features/radio/player/DesktopRadioPlayerController.kt` | 基于 JavaFX MediaPlayer 或 javax.sound |

### Phase 3: 视频播放器（预估 2-3 天）

| 任务 | 文件 | 说明 |
|------|------|------|
| 3.1 创建 DesktopVideoPlayerController | `shared/src/jvmMain/.../core/player/platform/DesktopVideoPlayerController.kt` | 基于 JavaFX MediaPlayer |
| 3.2 创建 PlatformVideoPlayerScreen (JVM) | `shared/src/jvmMain/.../core/player/ui/PlatformVideoPlayerScreen.jvm.kt` | Desktop 视频 UI |
| 3.3 创建 PlatformCacheDir (JVM) | `shared/src/jvmMain/.../core/player/cache/PlatformCacheDir.jvm.kt` | 缓存目录 |

### Phase 4: 兼容性修复（预估 1 天）

| 任务 | 文件 | 说明 |
|------|------|------|
| 4.1 处理 Android 特有 API | `VideoPlayerScreen.kt` 等 | 使用 expect/actual 抽取平台差异 |
| 4.2 处理 WindowInsets | 多个文件 | Desktop 上使用空实现 |
| 4.3 处理 BackHandler | `VideoPlayerScreen.kt` | Desktop 上监听键盘事件 |

### Phase 5: 测试与调试（预估 1-2 天）

| 任务 | 说明 |
|------|------|
| 5.1 运行 `./gradlew :desktopApp:run` | 验证 Desktop 启动 |
| 5.2 测试电台播放 | 验证音频流播放 |
| 5.3 测试视频播放 | 验证视频播放（需 JavaFX） |
| 5.4 测试导航和 UI | 验证所有页面切换 |

---

## 四、风险与注意事项

### 4.1 JavaFX 依赖风险

- **问题**：JavaFX 在 JDK 11+ 中已分离，需要单独添加依赖
- **解决方案**：
  - 使用 `org.openjfx:javafx-media:21` 作为 Gradle 依赖
  - 或在 JDK 中配置 `--module-path` 指向 JavaFX SDK
  - 替代方案：使用 VLCJ（VLC Java 绑定）替代 JavaFX MediaPlayer

### 4.2 HLS 播放支持

- **问题**：JavaFX MediaPlayer 对 HLS (m3u8) 的支持有限
- **解决方案**：
  - 使用 `ffmpeg` + Java 封装（如 `javacv`）
  - 或使用 VLCJ 播放 HLS 流
  - 或使用 ExoPlayer 的 JVM 移植版

### 4.3 Room 数据库 JVM 兼容性

- **问题**：Room KMP 在 JVM 上需要 SQLite JDBC
- **解决方案**：
  - 添加 `org.xerial:sqlite-jdbc` 依赖
  - 确保 `getDatabaseBuilder()` 在 JVM 上返回正确的 `RoomDatabase.Builder`

### 4.4 Compose Desktop 窗口管理

- **问题**：全屏模式在 Desktop 上的实现与 Android 不同
- **解决方案**：
  - 使用 `WindowState.isFullscreen` 控制全屏
  - 使用 `WindowFocusState` 监听窗口焦点

---

## 五、验证方法

```bash
# 1. 编译 Desktop 应用
./gradlew :desktopApp:compileKotlin

# 2. 运行 Desktop 应用
./gradlew :desktopApp:run

# 3. 打包 Desktop 应用
./gradlew :desktopApp:packageDmg        # macOS
./gradlew :desktopApp:packageMsi        # Windows
./gradlew :desktopApp:packageDeb        # Linux

# 4. 运行 Android 应用（确保未破坏）
./gradlew :androidApp:installDebug
```

---

## 六、Navigation 与 ViewModel 跨平台复用分析

### 6.1 Navigation（导航）— ✅ 完全可复用

项目中使用的 Navigation 依赖是：

```toml
# libs.versions.toml
navigation = "2.8.0-alpha13"
navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigation" }
```

这是 **JetBrains 官方维护的 KMP 版本** `org.jetbrains.androidx.navigation:navigation-compose`，**不是** AndroidX 的 `androidx.navigation:navigation-compose`。

**结论：Navigation 完全可跨平台复用，无需任何改造。**

项目中所有 Navigation 代码都在 `commonMain` 中：
- `App.kt` — `NavHost`、`rememberNavController`、`currentBackStackEntryAsState`
- `RadioNavigation.kt` — `composable`、`navigation`
- `FilmNavigation.kt` — `composable`、`navigation`、`navArgument`
- `DomesticNavigation.kt` — 同上

这些代码在 Desktop 上可以直接运行，**零改动**。

### 6.2 ViewModel — ✅ 完全可复用

项目中使用的 ViewModel 依赖是：

```toml
# libs.versions.toml
androidx-lifecycle = "2.10.0"
androidx-lifecycle-viewmodelCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle" }
androidx-lifecycle-runtimeCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "androidx-lifecycle" }
```

这是 **JetBrains 官方维护的 KMP 版本** `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose`，**不是** AndroidX 的 `androidx.lifecycle:lifecycle-viewmodel-compose`。

项目中所有 ViewModel 代码都在 `commonMain` 中：
- `BaseMviViewModel.kt` — 继承 `ViewModel`，使用 `viewModelScope`
- `RadioListViewModel.kt`、`RadioSearchViewModel.kt`
- `FilmViewModel.kt`、`FilmDetailViewModel.kt`、`FilmSearchViewModel.kt`
- `DomesticViewModel.kt`、`DomesticDetailViewModel.kt`、`DomesticSearchViewModel.kt`

**结论：ViewModel 完全可跨平台复用，无需任何改造。**

### 6.3 Koin ViewModel 注入 — ✅ 完全可复用

项目中使用的 Koin ViewModel 注入：

```kotlin
// 注册
viewModel { RadioListViewModel(get(), get()) }
viewModelOf(::FilmDetailViewModel)

// 注入
val viewModel = koinViewModel<RadioListViewModel>()
```

Koin 的 `koin-compose-viewmodel` 模块（`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` 的集成）也是跨平台的，在 Desktop 上同样可用。

### 6.4 需要关注的边界情况

| 组件 | 复用性 | 说明 |
|------|--------|------|
| `Navigation` | ✅ 完全复用 | JetBrains KMP 版，Desktop 原生支持 |
| `ViewModel` | ✅ 完全复用 | JetBrains KMP 版，Desktop 原生支持 |
| `viewModelScope` | ✅ 完全复用 | 跨平台实现 |
| `SavedStateHandle` | ✅ 完全复用 | 跨平台实现 |
| `Koin koinViewModel()` | ✅ 完全复用 | 跨平台实现 |
| `Paging` (app.cash.paging) | ✅ 完全复用 | 跨平台 KMP 库 |
| `Coil` (图片加载) | ✅ 完全复用 | Coil 3 原生 KMP 支持 |
| `Ktor` (网络请求) | ✅ 完全复用 | 原生 KMP 支持 |
| `Room` (数据库) | ⚠️ 需配置 | JVM 上需要 SQLite JDBC 驱动 |
| `compose-media-player` | ❌ 不可用 | 需替换为 Desktop 实现 |
| `Media3/ExoPlayer` | ❌ 不可用 | 需替换为 Desktop 实现 |

### 6.5 实际可复用的代码量估算

```
commonMain 代码总量 ≈ 15,000 行
其中：
  - Navigation 相关:    ~200 行  → 100% 复用
  - ViewModel 相关:    ~1,500 行 → 100% 复用
  - UI (Composable):   ~8,000 行 → ~90% 复用（仅播放器 UI 需平台适配）
  - Domain/Model:      ~2,000 行 → 100% 复用
  - Data/Repository:   ~2,000 行 → ~80% 复用（Room DAO 需 JVM 配置）
  - DI 模块:           ~1,000 行 → ~70% 复用（平台模块需替换）
  - 播放器相关:        ~1,000 行 → ~20% 复用（接口可复用，实现需替换）

总体复用率：~85%
需要新增的 jvmMain 代码：~1,500 行
```

---

## 七、总结

本改造方案的核心思路是 **"最小侵入、平台隔离"**：

1. **Navigation 和 ViewModel 完全可复用** — 项目已使用 JetBrains KMP 版本，Desktop 零改动
2. **不修改 commonMain 现有代码** — 所有平台差异通过 `expect/actual` 机制隔离
3. **充分利用 jvmMain** — 所有 Desktop 特有实现放在 `shared/src/jvmMain` 中
4. **JavaFX 作为首选播放引擎** — 内置于 JDK，无需额外原生库
5. **Koin 统一 DI** — Desktop 入口与 Android 使用相同的模块注册方式

预计总工作量：**5-9 天**（取决于 JavaFX 集成的复杂度和 HLS 播放需求）
其中 Navigation 和 ViewModel 部分 **无需任何改动**，可直接复用。
