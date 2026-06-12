package com.example.kmp_demo.features.domestic.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
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
    @Immutable
    data class State(
        val query: String = "",
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
 * 采用响应式流设计，使用 Paging 3 处理大数据量搜索结果。
 */
class DomesticSearchViewModel(
    private val repository: DomesticRepository,
    private val sensitiveWordFilter: SensitiveWordFilter,
) : BaseMviViewModel<DomesticSearchContract.State, DomesticSearchContract.Intent, DomesticSearchContract.Effect>(
    initialState = DomesticSearchContract.State()
) {

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searchResults: Flow<PagingData<DomesticMedia>> = uiState
        .map { it.query }
        .debounce(500)
        .distinctUntilChanged()
        .filter { it.isNotBlank() }
        .flatMapLatest { query ->
            // 🌟 本地敏感词前置拦截：命中则返回空 PagingData
            if (sensitiveWordFilter.containsSensitiveWord(query)) {
                updateState { copy(isBlocked = true) }
                flowOf(PagingData.empty())
            } else {
                updateState { copy(isBlocked = false) }
                repository.searchPaging(query)
            }
        }
        .cachedIn(viewModelScope)

    override fun sendIntent(intent: DomesticSearchContract.Intent) {
        when (intent) {
            is DomesticSearchContract.Intent.UpdateQuery -> {
                updateState { copy(query = intent.query, isBlocked = false) }
            }
            is DomesticSearchContract.Intent.ClearQuery -> {
                updateState { copy(query = "", isBlocked = false) }
            }
        }
    }
}
