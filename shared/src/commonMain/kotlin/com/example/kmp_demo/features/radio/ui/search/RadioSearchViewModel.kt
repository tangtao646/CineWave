package com.example.kmp_demo.features.radio.ui.search

import androidx.lifecycle.viewModelScope
import com.example.kmp_demo.core.*
import com.example.kmp_demo.features.radio.domain.model.RadioStation
import com.example.kmp_demo.features.radio.domain.repository.RadioRepository
import com.example.kmp_demo.features.radio.player.RadioPlayerManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RadioSearchContract {
    data class State(
        override val pageStatus: PageStatus = PageStatus.Content,
        val query: String = "",
        val results: List<RadioStation> = emptyList()
    ) : IBaseState

    sealed class Intent : IUiIntent {
        data class UpdateQuery(val query: String) : Intent()
        data object ClearQuery : Intent()
        data class ToggleFavorite(val station: RadioStation) : Intent()
        data class PlayStation(val station: RadioStation) : Intent()
    }

    sealed class Effect : IUiEffect
}

/**
 * 电台搜索 ViewModel - 迁移至 Koin
 */
class RadioSearchViewModel(
    private val repository: RadioRepository,
    val playerManager: RadioPlayerManager
) : BaseMviViewModel<RadioSearchContract.State, RadioSearchContract.Intent, RadioSearchContract.Effect>(
    initialState = RadioSearchContract.State()
) {

    init {
        // 监听搜索词变化并执行搜索
        viewModelScope.launch {
            uiState.map { it.query }
                .distinctUntilChanged()
                .debounce(500L)
                .collectLatest { query ->
                    if (query.isBlank()) {
                        updateState { copy(results = emptyList(), pageStatus = PageStatus.Content) }
                    } else {
                        performSearch(query)
                    }
                }
        }
    }

    private suspend fun performSearch(query: String) {
        updateState { copy(pageStatus = PageStatus.Loading) }
        try {
            val results = repository.searchStations(query, null)
            updateState {
                copy(
                    results = results,
                    pageStatus = if (results.isEmpty()) PageStatus.Empty else PageStatus.Content
                )
            }
        } catch (e: Exception) {
            updateState { copy(pageStatus = PageStatus.Error(e.message)) }
        }
    }

    override fun sendIntent(intent: RadioSearchContract.Intent) {
        when (intent) {
            is RadioSearchContract.Intent.UpdateQuery -> {
                updateState { copy(query = intent.query) }
            }
            is RadioSearchContract.Intent.ClearQuery -> {
                updateState { copy(query = "", results = emptyList(), pageStatus = PageStatus.Content) }
            }
            is RadioSearchContract.Intent.ToggleFavorite -> {
                viewModelScope.launch {
                    repository.toggleFavorite(intent.station.uuid, !intent.station.isFavorite)
                    // 局部更新列表状态
                    val newResults = uiState.value.results.map {
                        if (it.uuid == intent.station.uuid) it.copy(isFavorite = !it.isFavorite) else it
                    }
                    updateState { copy(results = newResults) }
                }
            }
            is RadioSearchContract.Intent.PlayStation -> {
                playerManager.playFromList(listOf(intent.station), 0)
            }
        }
    }
}
