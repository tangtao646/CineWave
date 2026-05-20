package com.example.kmp_demo.core.player.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.*

/**
 * [VideoPlayerManager] 单元测试
 *
 * 测试业务编排层的核心逻辑：
 * - 播放控制（play/pause/togglePlayPause）
 * - 进度控制（seekTo/seekForward/seekBackward）
 * - 全屏切换
 * - 控制栏显隐与自动隐藏
 * - 状态聚合（uiState）
 * - 资源释放
 */
class VideoPlayerManagerTest {

    private lateinit var fakeController: FakePlayerController
    private lateinit var manager: VideoPlayerManager

    @BeforeTest
    fun setup() {
        fakeController = FakePlayerController()
        manager = VideoPlayerManager(fakeController)
    }

    @AfterTest
    fun tearDown() {
        manager.release()
    }

    // ========== 打开视频源 ==========

    @Test
    fun `open should call controller open and show controls`() = runTest {
        manager.open("https://example.com/video.mp4")

        // 等待协程执行
        delay(100)

        assertEquals("open", fakeController.lastCalledMethod)
        assertEquals("https://example.com/video.mp4", fakeController.lastOpenedUrl)

        val state = manager.uiState.first { it.playbackState != VideoPlaybackState.IDLE }
        assertTrue(state.isControlsVisible)
    }

    @Test
    fun `open with headers should pass headers to controller`() = runTest {
        val headers = mapOf("Authorization" to "Bearer token123")
        manager.open("https://example.com/video.mp4", headers)

        delay(100)

        assertEquals(headers, fakeController.lastOpenedHeaders)
    }

    // ========== 播放/暂停 ==========

    @Test
    fun `play should call controller play`() = runTest {
        manager.play()
        delay(50)
        assertEquals("play", fakeController.lastCalledMethod)
    }

    @Test
    fun `pause should call controller pause`() = runTest {
        manager.pause()
        delay(50)
        assertEquals("pause", fakeController.lastCalledMethod)
    }

    @Test
    fun `togglePlayPause should call controller togglePlayPause`() = runTest {
        manager.togglePlayPause()
        delay(50)
        assertEquals("togglePlayPause", fakeController.lastCalledMethod)
    }

    // ========== 进度控制 ==========

    @Test
    fun `seekTo should call controller seekTo with correct position`() = runTest {
        manager.seekTo(30_000L)
        delay(50)
        assertEquals("seekTo", fakeController.lastCalledMethod)
        assertEquals(30_000L, fakeController.lastSeekPositionMs)
    }

    @Test
    fun `seekForward should call controller seekForward`() = runTest {
        manager.seekForward(30)
        delay(50)
        assertEquals("seekForward", fakeController.lastCalledMethod)
    }

    @Test
    fun `seekBackward should call controller seekBackward`() = runTest {
        manager.seekBackward(15)
        delay(50)
        assertEquals("seekBackward", fakeController.lastCalledMethod)
    }

    // ========== 全屏切换 ==========

    @Test
    fun `toggleFullScreen should call controller toggleFullScreen`() = runTest {
        manager.toggleFullScreen()
        delay(50)
        assertEquals("toggleFullScreen", fakeController.lastCalledMethod)
    }

    @Test
    fun `fullScreen state should be reflected in uiState`() = runTest {
        // 初始状态
        var state = manager.uiState.value
        assertFalse(state.isFullScreen)

        // 切换全屏
        manager.toggleFullScreen()
        delay(50)

        state = manager.uiState.value
        assertTrue(state.isFullScreen)
    }

    // ========== 音量控制 ==========

    @Test
    fun `setVolume should call controller setVolume`() = runTest {
        manager.setVolume(0.5f)
        delay(50)
        assertEquals("setVolume", fakeController.lastCalledMethod)
    }

    @Test
    fun `volume should be reflected in uiState`() = runTest {
        fakeController.setVolume(0.5f)
        delay(50)

        val state = manager.uiState.value
        assertEquals(0.5f, state.volume)
    }

    // ========== 控制栏显隐 ==========

    @Test
    fun `controls should be visible by default`() {
        assertTrue(manager.uiState.value.isControlsVisible)
    }

    @Test
    fun `toggleControls should toggle visibility`() {
        assertTrue(manager.uiState.value.isControlsVisible)

        manager.toggleControls()
        assertFalse(manager.uiState.value.isControlsVisible)

        manager.toggleControls()
        assertTrue(manager.uiState.value.isControlsVisible)
    }

    @Test
    fun `showControls should make controls visible`() {
        manager.hideControls()
        assertFalse(manager.uiState.value.isControlsVisible)

        manager.showControls()
        assertTrue(manager.uiState.value.isControlsVisible)
    }

    @Test
    fun `hideControls should make controls hidden`() {
        manager.hideControls()
        assertFalse(manager.uiState.value.isControlsVisible)
    }

    @Test
    fun `controls should auto-hide after delay`() = runTest {
        manager.setAutoHideDelay(100) // 100ms 后自动隐藏
        manager.showControls()

        assertTrue(manager.uiState.value.isControlsVisible)

        delay(200) // 等待超过自动隐藏延迟

        assertFalse(manager.uiState.value.isControlsVisible)
    }

    @Test
    fun `showing controls should restart auto-hide timer`() = runTest {
        manager.setAutoHideDelay(200)

        manager.showControls()
        delay(100)
        manager.showControls() // 重置计时器
        delay(150) // 如果没重置，此时已隐藏；重置了则还显示

        assertTrue(manager.uiState.value.isControlsVisible)

        delay(100) // 等待重置后的计时器到期

        assertFalse(manager.uiState.value.isControlsVisible)
    }

    // ========== 状态聚合 ==========

    @Test
    fun `uiState should aggregate all controller states`() = runTest {
        fakeController.setPlaybackState(VideoPlaybackState.PLAYING)
        fakeController.setPosition(30_000L)
        fakeController.setDuration(120_000L)
        fakeController.setBufferedPercent(50)
        fakeController.setVolume(0.8f)
        fakeController.setFullScreen(true)

        delay(100) // 等待状态传播

        val state = manager.uiState.value
        assertEquals(VideoPlaybackState.PLAYING, state.playbackState)
        assertEquals(30_000L, state.currentPosition)
        assertEquals(120_000L, state.duration)
        assertEquals(50, state.bufferedPercent)
        assertEquals(0.8f, state.volume)
        assertTrue(state.isFullScreen)
        assertTrue(state.isPlaying)
        assertFalse(state.isBuffering)
        assertEquals(0.25f, state.progressFraction)
    }

    @Test
    fun `uiState should reflect error state`() = runTest {
        fakeController.setPlaybackState(VideoPlaybackState.ERROR)
        delay(50)

        val state = manager.uiState.value
        assertEquals(VideoPlaybackState.ERROR, state.playbackState)
        assertNotNull(state.error)
    }

    // ========== 资源释放 ==========

    @Test
    fun `release should release controller and cancel scope`() {
        manager.release()
        assertTrue(fakeController.isReleased)
    }

    @Test
    fun `multiple release calls should not throw`() {
        manager.release()
        manager.release() // 第二次调用不应抛出异常
    }
}
