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
 * ## 使用方式
 * ```kotlin
 * val autoHideService = ControlsAutoHideService()
 * autoHideService.show()  // 显示并重启计时器
 * autoHideService.hide()  // 立即隐藏并取消计时器
 * autoHideService.toggle() // 切换显隐
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

    private fun restartTimer() {
        autoHideJob?.cancel()
        autoHideJob = scope.launch {
            delay(autoHideDelayMs)
            _isControlsVisible.value = false
        }
    }
}
