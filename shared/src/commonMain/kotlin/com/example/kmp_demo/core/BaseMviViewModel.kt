package com.example.kmp_demo.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI 架构通用基类
 *
 * 使用 JetBrains KMP 版本的 ViewModel (org.jetbrains.androidx.lifecycle.ViewModel)
 * 通过 libs.versions.toml 中的 androidx-lifecycle-viewmodelCompose 引入
 */
abstract class BaseMviViewModel<S : IUiState, I : IUiIntent, E : IUiEffect>(
    initialState: S
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState = _uiState.asStateFlow()

    // 使用 CONFLATED：UI 层只消费最新的副作用，避免页面恢复后消费过期事件
    private val _uiEffect = Channel<E>(Channel.CONFLATED)
    val uiEffect = _uiEffect.receiveAsFlow()

    /**
     * 当前状态快照
     */
    protected val currentState: S get() = uiState.value

    /**
     * 发送意图 (由 UI 调用)
     */
    abstract fun sendIntent(intent: I)

    /**
     * 更新 UI 状态 (由子类 ViewModel 调用)
     */
    protected fun updateState(reducer: S.() -> S) {
        _uiState.update { it.reducer() }
    }

    /**
     * 发送一次性副作用 (由子类 ViewModel 调用)
     */
    protected fun sendEffect(effect: E) {
        viewModelScope.launch {
            _uiEffect.send(effect)
        }
    }
}
