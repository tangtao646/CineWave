package com.example.kmp_demo.features.domestic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kmp_demo.core.security.SensitiveWordFilter
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import com.example.kmp_demo.features.domestic.domain.repository.DomesticRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 国内影视搜索页 ViewModel。
 *
 * 使用 debounce 实现输入防抖，通过 [SensitiveWordFilter] 做本地前置拦截。
 */
class DomesticSearchViewModel(
    private val repository: DomesticRepository,
    private val sensitiveWordFilter: SensitiveWordFilter,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val results: List<DomesticMedia> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        /** 是否因命中敏感词而拦截 */
        val isBlocked: Boolean = true,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _queryFlow = MutableStateFlow("")

    init {
        observeSearch()
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun observeSearch() {
        _queryFlow
            .debounce(500)
            .distinctUntilChanged()
            .filter { it.isNotBlank() }
            .flatMapLatest { query ->
                flow {
                    _uiState.value = _uiState.value.copy(isLoading = true, error = null, isBlocked = false)

                    // 🌟 本地敏感词前置拦截
                    if (sensitiveWordFilter.containsSensitiveWord(query)) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isBlocked = true,
                        )
                        return@flow
                    }

                    try {
                        val results = repository.search(query)
                        emit(results)
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.message,
                        )
                    }
                }
            }
            .onEach { results ->
                _uiState.value = _uiState.value.copy(
                    results = results,
                    isLoading = false,
                )
            }
            .launchIn(viewModelScope)
    }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        _queryFlow.value = query
    }

    fun clearQuery() {
        _uiState.value = UiState()
        _queryFlow.value = ""
    }
}
