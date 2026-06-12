package com.example.kmp_demo.features.domestic.ui

import androidx.lifecycle.viewModelScope
import com.example.kmp_demo.core.BaseMviViewModel
import com.example.kmp_demo.core.IUiEffect
import com.example.kmp_demo.core.IUiIntent
import com.example.kmp_demo.core.IUiState
import com.example.kmp_demo.core.security.SensitiveWordFilter
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import com.example.kmp_demo.features.domestic.domain.repository.DomesticRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

/**
 * 国内影视搜索页 MVI 协议。
 */
class DomesticSearchContract {
    data class State(
        val query: String = "",
        val results: List<DomesticMedia> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        /** 是否因命中敏感词而拦截 */
        val isBlocked: Boolean = true,
    ) : IUiState

    sealed class Intent : IUiIntent {
        /** 更新搜索词 */
        data class UpdateQuery(val query: String) : Intent()
        /** 清除搜索 */
        data object ClearQuery : Intent()
    }

    sealed class Effect : IUiEffect
}

/**
 * 国内影视搜索页 ViewModel。
 *
 * 遵循 MVI 架构，继承自 [BaseMviViewModel]。
 * 使用 debounce 实现输入防抖，通过 [SensitiveWordFilter] 做本地前置拦截。
 */
class DomesticSearchViewModel(
    private val repository: DomesticRepository,
    private val sensitiveWordFilter: SensitiveWordFilter,
) : BaseMviViewModel<DomesticSearchContract.State, DomesticSearchContract.Intent, DomesticSearchContract.Effect>(
    initialState = DomesticSearchContract.State()
) {

    private val _queryFlow = MutableStateFlow("")

    init {
        observeSearch()
    }

    override fun sendIntent(intent: DomesticSearchContract.Intent) {
        when (intent) {
            is DomesticSearchContract.Intent.UpdateQuery -> {
                updateState { copy(query = intent.query) }
                _queryFlow.value = intent.query
            }
            is DomesticSearchContract.Intent.ClearQuery -> {
                updateState { DomesticSearchContract.State() }
                _queryFlow.value = ""
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun observeSearch() {
        _queryFlow
            .debounce(500)
            .distinctUntilChanged()
            .filter { it.isNotBlank() }
            .flatMapLatest { query ->
                flow {
                    updateState { copy(isLoading = true, error = null, isBlocked = false) }

                    // 🌟 本地敏感词前置拦截
                    if (sensitiveWordFilter.containsSensitiveWord(query)) {
                        updateState {
                            copy(
                                isLoading = false,
                                isBlocked = true,
                            )
                        }
                        return@flow
                    }

                    try {
                        val results = repository.search(query)
                        emit(results)
                    } catch (e: Exception) {
                        updateState {
                            copy(
                                isLoading = false,
                                error = e.message,
                            )
                        }
                    }
                }
            }
            .onEach { results ->
                updateState {
                    copy(
                        results = results,
                        isLoading = false,
                    )
                }
            }
            .launchIn(viewModelScope)
    }
}
