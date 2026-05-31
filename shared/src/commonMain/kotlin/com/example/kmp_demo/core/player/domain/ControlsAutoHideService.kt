package com.example.kmp_demo.core.player.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 控制栏自动隐藏服务。
 *
 * 独立于 [VideoPlayerManager]，职责单一：管理控制栏的自动隐藏计时。
 * 可被 [VideoPlayerManager] 组合使用，也可被其他组件独立使用。
 *
 * ## 交互感知（解决倒计时被用户操作打断的问题）
 *
 * 当用户正在与控制栏交互时（如拖拽进度条、调节音量），
 * 调用 [beginInteraction] 暂停倒计时，调用 [endInteraction] 恢复倒计时。
 * 这确保用户在操作过程中控制栏不会突然消失。
 *
 * ## 设计原则
 *
 * 采用"主流播放器做法"：用户结束交互后，**始终给予完整的倒计时**，
 * 而不是抠字眼计算扣除交互前浪费的时间。这消除了：
 * - 竞态条件：不再依赖 [remainingMs] 和 [timerStartNanos] 的精确时序
 * - 极端弱网/卡顿导致 remainingMs 归零，松手瞬间控制栏消失的生硬体验
 *
 * ## 使用方式
 * ```kotlin
 * val autoHideService = ControlsAutoHideService()
 * autoHideService.show()  // 显示并重启计时器
 * autoHideService.hide()  // 立即隐藏并取消计时器
 * autoHideService.toggle() // 切换显隐
 *
 * // 用户开始交互时（如按下进度条）
 * autoHideService.beginInteraction()
 * // 用户结束交互时（如松开进度条）
 * autoHideService.endInteraction()
 * ```
 */
class ControlsAutoHideService {
    private val _isControlsVisible = MutableStateFlow(true)

    /** 控制栏是否可见 */
    val isControlsVisible: StateFlow<Boolean> = _isControlsVisible.asStateFlow()

    /** 复用类级别协程作用域，避免每次 restartTimer 创建新 scope */
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var autoHideJob: Job? = null
    private var autoHideDelayMs: Long = 3000L

    /**
     * 用户是否正在与控制栏交互。
     *
     * 当 [isInteracting] 为 true 时，自动隐藏计时器暂停运行。
     * 用户结束交互后，计时器从头开始完整的倒计时。
     */
    private var isInteracting: Boolean = false

    /**
     * 显示控制栏并重启自动隐藏计时器。
     */
    fun show() {
        _isControlsVisible.value = true
        restartTimer()
    }

    /**
     * 隐藏控制栏并取消自动隐藏计时器。
     */
    fun hide() {
        _isControlsVisible.value = false
        autoHideJob?.cancel()
        isInteracting = false
    }

    /**
     * 切换控制栏显隐。
     * - 如果当前可见，则隐藏
     * - 如果当前隐藏，则显示并重启计时器
     */
    fun toggle() {
        if (_isControlsVisible.value) hide() else show()
    }

    /**
     * 设置自动隐藏延迟时间。
     * @param delayMs 延迟毫秒数，默认 3000ms
     */
    fun setAutoHideDelay(delayMs: Long) {
        autoHideDelayMs = delayMs
    }

    /**
     * 取消自动隐藏计时器（不改变当前显隐状态）。
     */
    fun cancelTimer() {
        autoHideJob?.cancel()
    }

    /**
     * 标记用户开始与控制栏交互。
     *
     * 调用此方法后，自动隐藏计时器将暂停，
     * 控制栏保持可见，直到用户调用 [endInteraction]。
     *
     * 适用于：
     * - 用户按下进度条滑块（开始拖拽）
     * - 用户按下音量滑块（开始调节）
     * - 用户长按快进/快退按钮
     */
    fun beginInteraction() {
        if (!_isControlsVisible.value || isInteracting) return
        isInteracting = true

        // 取消当前计时器，暂停倒计时
        autoHideJob?.cancel()
        autoHideJob = null
    }

    /**
     * 标记用户结束与控制栏交互。
     *
     * 调用此方法后，自动隐藏计时器**从头开始完整的倒计时**。
     * 这是主流播放器的做法（如 YouTube、Infuse），
     * 避免因弱网/卡顿导致剩余时间归零而生硬消失。
     *
     * 适用于：
     * - 用户松开进度条滑块（结束拖拽）
     * - 用户松开音量滑块（结束调节）
     * - 用户松开快进/快退按钮
     */
    fun endInteraction() {
        if (!isInteracting) return
        isInteracting = false

        // 用户结束交互后，始终给予完整的倒计时
        if (_isControlsVisible.value) {
            restartTimer()
        }
    }

    /**
     * 显示控制栏并重置倒计时（用于用户点击控制栏按钮时）。
     *
     * 与 [show] 的区别：
     * - [show]：强制显示并从头开始倒计时（即使正在交互也会重置）
     * - [showAndResetTimer]：如果控制栏已可见，仅重置倒计时；如果不可见，则显示
     *
     * 适用于用户点击控制栏上的按钮（快进、快退、音量等）时调用，
     * 确保用户操作后倒计时重新开始。
     */
    fun showAndResetTimer() {
        if (!_isControlsVisible.value) {
            _isControlsVisible.value = true
        }
        restartTimer()
    }

    private fun restartTimer() {
        autoHideJob?.cancel()
        isInteracting = false

        autoHideJob = scope.launch {
            delay(autoHideDelayMs)
            _isControlsVisible.value = false
        }
    }
}
