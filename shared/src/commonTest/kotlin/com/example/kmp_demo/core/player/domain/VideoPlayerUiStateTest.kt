package com.example.kmp_demo.core.player.domain

import kotlin.test.Test
import kotlin.test.*

/**
 * [VideoPlayerUiState] 单元测试
 *
 * 测试状态聚合模型的计算属性是否正确：
 * - progressFraction: 进度百分比
 * - currentPositionText / durationText: 时间格式化
 * - isPlaying / isBuffering: 播放状态判断
 */
class VideoPlayerUiStateTest {

    @Test
    fun `default state should have correct initial values`() {
        val state = VideoPlayerUiState()

        assertEquals(VideoPlaybackState.IDLE, state.playbackState)
        assertEquals(0L, state.currentPosition)
        assertEquals(0L, state.duration)
        assertEquals(0, state.bufferedPercent)
        assertEquals(1.0f, state.volume)
        assertFalse(state.isFullScreen)
        assertTrue(state.isControlsVisible)
        assertNull(state.error)
        assertFalse(state.isPlaying)
        assertFalse(state.isBuffering)
        assertEquals(0f, state.progressFraction)
        assertEquals("00:00", state.currentPositionText)
        assertEquals("00:00", state.durationText)
    }

    @Test
    fun `progressFraction should be 0 when duration is 0`() {
        val state = VideoPlayerUiState(
            currentPosition = 5000L,
            duration = 0L
        )
        assertEquals(0f, state.progressFraction)
    }

    @Test
    fun `progressFraction should calculate correctly`() {
        val state = VideoPlayerUiState(
            currentPosition = 30_000L,
            duration = 120_000L
        )
        assertEquals(0.25f, state.progressFraction)
    }

    @Test
    fun `progressFraction should be clamped between 0 and 1`() {
        val overState = VideoPlayerUiState(
            currentPosition = 200_000L,
            duration = 120_000L
        )
        assertEquals(1f, overState.progressFraction)

        val underState = VideoPlayerUiState(
            currentPosition = -1000L,
            duration = 120_000L
        )
        assertEquals(0f, underState.progressFraction)
    }

    @Test
    fun `isPlaying should be true only when playbackState is PLAYING`() {
        assertFalse(VideoPlayerUiState(playbackState = VideoPlaybackState.IDLE).isPlaying)
        assertFalse(VideoPlayerUiState(playbackState = VideoPlaybackState.BUFFERING).isPlaying)
        assertFalse(VideoPlayerUiState(playbackState = VideoPlaybackState.READY).isPlaying)
        assertTrue(VideoPlayerUiState(playbackState = VideoPlaybackState.PLAYING).isPlaying)
        assertFalse(VideoPlayerUiState(playbackState = VideoPlaybackState.PAUSED).isPlaying)
        assertFalse(VideoPlayerUiState(playbackState = VideoPlaybackState.ENDED).isPlaying)
        assertFalse(VideoPlayerUiState(playbackState = VideoPlaybackState.ERROR).isPlaying)
    }

    @Test
    fun `isBuffering should be true only when playbackState is BUFFERING`() {
        assertFalse(VideoPlayerUiState(playbackState = VideoPlaybackState.IDLE).isBuffering)
        assertTrue(VideoPlayerUiState(playbackState = VideoPlaybackState.BUFFERING).isBuffering)
        assertFalse(VideoPlayerUiState(playbackState = VideoPlaybackState.READY).isBuffering)
        assertFalse(VideoPlayerUiState(playbackState = VideoPlaybackState.PLAYING).isBuffering)
        assertFalse(VideoPlayerUiState(playbackState = VideoPlaybackState.PAUSED).isBuffering)
        assertFalse(VideoPlayerUiState(playbackState = VideoPlaybackState.ENDED).isBuffering)
        assertFalse(VideoPlayerUiState(playbackState = VideoPlaybackState.ERROR).isBuffering)
    }

    @Test
    fun `error should be set when playbackState is ERROR`() {
        val state = VideoPlayerUiState(playbackState = VideoPlaybackState.ERROR)
        assertNotNull(state.error)
    }

    @Test
    fun `formatDuration should format milliseconds correctly`() {
        // Less than 1 hour
        assertEquals("00:00", VideoPlayerUiState.formatDuration(0))
        assertEquals("00:01", VideoPlayerUiState.formatDuration(1000))
        assertEquals("01:00", VideoPlayerUiState.formatDuration(60_000))
        assertEquals("01:30", VideoPlayerUiState.formatDuration(90_000))
        assertEquals("59:59", VideoPlayerUiState.formatDuration(3_599_000))

        // More than 1 hour
        assertEquals("1:00:00", VideoPlayerUiState.formatDuration(3_600_000))
        assertEquals("1:01:30", VideoPlayerUiState.formatDuration(3_690_000))
        assertEquals("2:30:00", VideoPlayerUiState.formatDuration(9_000_000))
    }

    @Test
    fun `currentPositionText and durationText should use formatDuration`() {
        val state = VideoPlayerUiState(
            currentPosition = 65_000L,
            duration = 180_000L
        )
        assertEquals("01:05", state.currentPositionText)
        assertEquals("03:00", state.durationText)
    }

    @Test
    fun `copy should create independent state`() {
        val state1 = VideoPlayerUiState(
            playbackState = VideoPlaybackState.PLAYING,
            currentPosition = 10_000L,
            duration = 60_000L
        )
        val state2 = state1.copy(currentPosition = 20_000L)

        assertEquals(10_000L, state1.currentPosition)
        assertEquals(20_000L, state2.currentPosition)
        assertEquals(state1.playbackState, state2.playbackState)
        assertEquals(state1.duration, state2.duration)
    }
}
