package com.example.kmp_demo.features.domestic.ui

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.kmp_demo.core.BaseMviViewModel
import com.example.kmp_demo.core.IUiEffect
import com.example.kmp_demo.core.IUiIntent
import com.example.kmp_demo.core.IUiState
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import com.example.kmp_demo.features.domestic.domain.repository.DomesticRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 国内影视首页 MVI 协议。
 *
 * 参考 [FilmContract] 的 MVI 模式。
 */
class DomesticContract {
    data class State(
        val availableTypes: List<String> = emptyList(),
        val selectedType: String = "全部", // "全部" 表示不过滤
        val isLoadingTypes: Boolean = false,
    ) : IUiState

    sealed class Intent : IUiIntent {
        data object Refresh : Intent()
        data class SelectType(val typeName: String) : Intent()
    }

    sealed class Effect : IUiEffect
}

/**
 * 国内影视首页 ViewModel。
 *
 * 使用 Paging 3 + Room 缓存驱动瀑布流数据。
 * 分类切换通过 [MutableStateFlow] + [flatMapLatest] 响应式切换 Paging 数据源。
 *
 * 参考 [FilmViewModel] 的 Paging 模式。
 */
class DomesticViewModel(
    private val repository: DomesticRepository,
) : BaseMviViewModel<DomesticContract.State, DomesticContract.Intent, DomesticContract.Effect>(
    initialState = DomesticContract.State()
) {

    private val _selectedType = MutableStateFlow("全部")

    /**
     * Paging 数据流，响应分类切换。
     * 使用 [cachedIn] 在配置变更时保持数据。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val mediaPaging: Flow<PagingData<DomesticMedia>> = _selectedType
        .flatMapLatest { typeName ->
            repository.getRecentMediaPaging(typeName)
        }
        .cachedIn(viewModelScope)

    init {
        fetchTypes()
    }

    override fun sendIntent(intent: DomesticContract.Intent) {
        when (intent) {
            is DomesticContract.Intent.Refresh -> {
                // Paging 刷新由 UI 层调用 mediaPaging 的 refresh()
            }

            is DomesticContract.Intent.SelectType -> {
                _selectedType.value = intent.typeName
                updateState { copy(selectedType = intent.typeName) }
            }
        }
    }

    private fun fetchTypes() {
        viewModelScope.launch {
            updateState { copy(isLoadingTypes = true) }
            try {
                val types = repository.getAvailableTypes()
                updateState { copy(availableTypes = types, isLoadingTypes = false) }
            } catch (_: Exception) {
                updateState { copy(isLoadingTypes = false) }
            }
        }
    }
}
