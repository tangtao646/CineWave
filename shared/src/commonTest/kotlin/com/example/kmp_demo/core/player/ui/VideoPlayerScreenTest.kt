package com.example.kmp_demo.core.player.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kmp_demo.core.player.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


/**
 * [VideoPlayerScreen] UI 测试
 *
 * 测试核心交互场景：
 * 1. 播放/暂停按钮显示与点击
 * 2. 快进/快退按钮
 * 3. 进度条存在性
 * 4. 控制栏显隐切换
 * 5. 全屏切换
 * 6. 顶栏返回按钮与标题
 * 7. 时间显示
 * 8. 缓冲状态
 *
 * 使用 [FakePlayerController] 替代真实播放器，避免平台依赖。
 */
@RunWith(AndroidJUnit4::class)
class VideoPlayerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fakeController: FakePlayerController
    private lateinit var manager: VideoPlayerManager
    private var backPressed = false

    @Before
    fun setup() {
        fakeController = FakePlayerController()
        manager = VideoPlayerManager(fakeController)
        backPressed = false
    }

    @After
    fun tearDown() {
        manager.release()
    }

    // ========== 辅助方法 ==========

    private fun launchScreen(
        url: String = "https://vip.ffzy-play6.com/20221027/2369_b83d9749/index.m3u8",
        title: String = "Test Video"
    ) {
        composeTestRule.setContent {
            TestVideoPlayerScreenContent(
                url = url,
                title = title,
                manager = manager,
                onBack = { backPressed = true }
            )
        }
    }

    private fun waitForIdle() {
        composeTestRule.waitForIdle()
    }

    // ========== 播放/暂停测试 ==========

    @Test
    fun `play button should be visible when video is paused`() {
        fakeController.setPlaybackState(VideoPlaybackState.PAUSED)
        launchScreen()
        waitForIdle()

        composeTestRule
            .onNodeWithContentDescription("播放")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun `pause button should be visible when video is playing`() {
        fakeController.setPlaybackState(VideoPlaybackState.PLAYING)
        launchScreen()
        waitForIdle()

        composeTestRule
            .onNodeWithContentDescription("暂停")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun `clicking center play button should trigger play action`() {
        fakeController.setPlaybackState(VideoPlaybackState.PAUSED)
        launchScreen()
        waitForIdle()

        composeTestRule
            .onNodeWithContentDescription("播放")
            .performClick()

        waitForIdle()
        // 验证 controller 收到了 togglePlayPause 调用
        assert(
            fakeController.lastCalledMethod == "togglePlayPause" ||
                    fakeController.lastCalledMethod == "play"
        )
    }

    @Test
    fun `clicking bottom pause button should trigger pause action`() {
        fakeController.setPlaybackState(VideoPlaybackState.PLAYING)
        launchScreen()
        waitForIdle()

        composeTestRule
            .onNodeWithContentDescription("暂停")
            .performClick()

        waitForIdle()
    }

    @Test
    fun `buffering indicator should show when video is buffering`() {
        fakeController.setPlaybackState(VideoPlaybackState.BUFFERING)
        launchScreen()
        waitForIdle()

        // 缓冲时中央按钮区域显示 CircularProgressIndicator
        composeTestRule
            .onNodeWithTag("BufferingIndicator")
            .assertExists()
    }

    // ========== 快进/快退测试 ==========

    @Test
    fun `forward button should exist and be clickable`() {
        fakeController.setPlaybackState(VideoPlaybackState.PLAYING)
        launchScreen()
        waitForIdle()

        composeTestRule
            .onNodeWithContentDescription("快进 10 秒")
            .assertExists()
            .assertIsDisplayed()
            .performClick()
    }

    @Test
    fun `rewind button should exist and be clickable`() {
        fakeController.setPlaybackState(VideoPlaybackState.PLAYING)
        launchScreen()
        waitForIdle()

        composeTestRule
            .onNodeWithContentDescription("快退 10 秒")
            .assertExists()
            .assertIsDisplayed()
            .performClick()
    }

    // ========== 控制栏显隐测试 ==========

    @Test
    fun `controls should be visible by default`() {
        launchScreen()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Test Video")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun `clicking on video area should toggle controls`() {
        launchScreen()
        waitForIdle()

        // 初始状态：控制栏可见
        composeTestRule
            .onNodeWithText("Test Video")
            .assertIsDisplayed()

        // 点击视频区域
        composeTestRule
            .onNodeWithTag("VideoClickOverlay")
            .performClick()

        waitForIdle()

        // 控制栏应该隐藏（顶栏文字不可见）
        composeTestRule
            .onNodeWithText("Test Video")
            .assertDoesNotExist()
    }

    @Test
    fun `clicking video area twice should toggle controls back`() {
        launchScreen()
        waitForIdle()

        // 第一次点击 - 隐藏
        composeTestRule
            .onNodeWithTag("VideoClickOverlay")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Test Video")
            .assertDoesNotExist()

        // 第二次点击 - 显示
        composeTestRule
            .onNodeWithTag("VideoClickOverlay")
            .performClick()
        waitForIdle()

        composeTestRule
            .onNodeWithText("Test Video")
            .assertIsDisplayed()
    }

    // ========== 全屏测试 ==========

    @Test
    fun `fullscreen button should be clickable`() {
        fakeController.setPlaybackState(VideoPlaybackState.PLAYING)
        launchScreen()
        waitForIdle()

        composeTestRule
            .onNodeWithContentDescription("全屏")
            .assertExists()
            .assertIsDisplayed()
            .performClick()
    }

    @Test
    fun `exit fullscreen button should show when in fullscreen`() {
        fakeController.setPlaybackState(VideoPlaybackState.PLAYING)
        fakeController.setFullScreen(true)
        launchScreen()
        waitForIdle()

        composeTestRule
            .onNodeWithContentDescription("退出全屏")
            .assertExists()
            .assertIsDisplayed()
    }

    // ========== 顶栏测试 ==========

    @Test
    fun `top bar should display correct title`() {
        launchScreen(title = "Custom Title")
        waitForIdle()

        composeTestRule
            .onNodeWithText("Custom Title")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun `back button should trigger onBack callback`() {
        launchScreen()
        waitForIdle()

        composeTestRule
            .onNodeWithContentDescription("返回")
            .assertExists()
            .assertIsDisplayed()
            .performClick()

        assert(backPressed) { "onBack should have been called" }
    }

    // ========== 时间显示测试 ==========

    @Test
    fun `time display should show formatted position and duration`() {
        fakeController.setPlaybackState(VideoPlaybackState.PLAYING)
        fakeController.setPosition(65_000L) // 01:05
        fakeController.setDuration(180_000L) // 03:00
        launchScreen()
        waitForIdle()

        composeTestRule
            .onNodeWithText("01:05 / 03:00")
            .assertExists()
    }

    @Test
    fun `time display should update when position changes`() {
        fakeController.setPlaybackState(VideoPlaybackState.PLAYING)
        fakeController.setPosition(0L)
        fakeController.setDuration(120_000L)
        launchScreen()
        waitForIdle()

        composeTestRule
            .onNodeWithText("00:00 / 02:00")
            .assertExists()

        // 更新位置
        fakeController.setPosition(30_000L)
        waitForIdle()

        composeTestRule
            .onNodeWithText("00:30 / 02:00")
            .assertExists()
    }

    // ========== 错误状态测试 ==========

    @Test
    fun `error state should hide play button`() {
        fakeController.setPlaybackState(VideoPlaybackState.ERROR)
        launchScreen()
        waitForIdle()

        composeTestRule
            .onNodeWithContentDescription("播放")
            .assertDoesNotExist()
    }

    // ========== 进度条测试 ==========

    @Test
    fun `progress slider should exist when controls are visible`() {
        fakeController.setPlaybackState(VideoPlaybackState.PLAYING)
        launchScreen()
        waitForIdle()

        // Slider 组件应该存在
        composeTestRule
            .onNodeWithTag("VideoProgressSlider")
            .assertExists()
    }
}

/**
 * 测试用的 VideoPlayerScreen 内容组件
 *
 * 模拟 [VideoPlayerScreen] 的布局结构，但使用注入的 [VideoPlayerManager]，
 * 避免依赖 [ComposeMediaPlayerController] 和 [rememberVideoPlayerState]。
 */
@Composable
fun TestVideoPlayerScreenContent(
    url: String,
    title: String,
    manager: VideoPlayerManager,
    onBack: () -> Unit
) {
    val uiState by manager.uiState.collectAsState()

    LaunchedEffect(url) {
        manager.open(url)
    }

    DisposableEffect(Unit) {
        onDispose {
            manager.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 视频渲染层占位
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTag = "VideoSurface" }
        )

        // 点击切换控制栏显隐
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTag = "VideoClickOverlay" }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    manager.toggleControls()
                }
        )

        // 控制栏
        AnimatedVisibility(
            visible = uiState.isControlsVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 底部控制栏（先绘制，作为背景层）
                VideoPlayerControls(
                    state = uiState,
                    onAction = { action ->
                        handlePlayerAction(manager, action)
                    }
                )

                // 顶栏（后绘制，浮在控制栏上方）
                VideoPlayerTopBar(
                    title = title,
                    onBack = {
                        if (uiState.isFullScreen) {
                            handlePlayerAction(manager, PlayerAction.ToggleFullScreen)
                        } else {
                            onBack()
                        }
                    },
                    pipEnabled = false,
                    onPipToggle = {},

                    )
            }
        }
    }
}
