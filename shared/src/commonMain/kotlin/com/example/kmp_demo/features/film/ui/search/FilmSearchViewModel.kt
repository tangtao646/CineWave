package com.example.kmp_demo.features.film.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import app.cash.paging.filter
import com.example.kmp_demo.core.BaseMviViewModel
import com.example.kmp_demo.core.IUiEffect
import com.example.kmp_demo.core.IUiIntent
import com.example.kmp_demo.core.IUiState
import com.example.kmp_demo.core.security.SensitiveWordFilter
import com.example.kmp_demo.features.film.domain.model.Movie
import com.example.kmp_demo.features.film.domain.repository.FilmRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

/**
 * 契约类：定义状态、意图、副作用
 */
class FilmSearchContract {
    @Immutable
    data class State(
        val query: String = "",
        /** 是否因命中敏感词而拦截 */
        val isBlocked: Boolean = true,
    ) : IUiState

    sealed class Intent : IUiIntent {
        data class UpdateQuery(val query: String) : Intent()
        data object ClearQuery : Intent()
    }

    sealed class Effect : IUiEffect {
        data class ShowToast(val message: String) : Effect()
    }
}

class FilmSearchViewModel(
    private val repository: FilmRepository,
    private val sensitiveWordFilter: SensitiveWordFilter,
) : BaseMviViewModel<FilmSearchContract.State, FilmSearchContract.Intent, FilmSearchContract.Effect>(
    initialState = FilmSearchContract.State()
) {

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searchResults: Flow<PagingData<Movie>> = uiState
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
                repository.searchMovies(query)
            }
        }
        .cachedIn(viewModelScope)

    override fun sendIntent(intent: FilmSearchContract.Intent) {
        when (intent) {
            is FilmSearchContract.Intent.UpdateQuery -> {
                updateState { copy(query = intent.query, isBlocked = false) }
            }
            FilmSearchContract.Intent.ClearQuery -> {
                updateState { copy(query = "", isBlocked = false) }
            }
        }
    }
}
