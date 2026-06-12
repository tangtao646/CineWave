package com.example.kmp_demo.features.film.ui

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.kmp_demo.core.BaseMviViewModel
import com.example.kmp_demo.core.IUiEffect
import com.example.kmp_demo.core.IUiIntent
import com.example.kmp_demo.core.IUiState
import com.example.kmp_demo.features.film.data.remote.dto.GenreDto
import com.example.kmp_demo.features.film.domain.model.Movie
import com.example.kmp_demo.features.film.domain.model.MovieSortOrder
import com.example.kmp_demo.features.film.domain.repository.FilmRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 电影首页 MVI 协议
 */
class FilmContract {
    data class State(
        val genres: List<GenreDto> = emptyList(),
        val selectedGenreId: String? = null, // null 代表 "热门" 或 "全部"
        val sortOrder: MovieSortOrder = MovieSortOrder.VOTE_AVERAGE_DESC,
        val isLoadingGenres: Boolean = false
    ) : IUiState

    sealed class Intent : IUiIntent {
        data object Refresh : Intent()
        data class SelectGenre(val genreId: String?) : Intent()
        data class SelectSortOrder(val sortOrder: MovieSortOrder) : Intent()
    }

    sealed class Effect : IUiEffect
}

/**
 * 电影首页 ViewModel。
 *
 * 采用响应式设计，Paging 数据流根据 [uiState] 中的分类和排序动态切换。
 */
class FilmViewModel(
    private val repository: FilmRepository
) : BaseMviViewModel<FilmContract.State, FilmContract.Intent, FilmContract.Effect>(
    initialState = FilmContract.State()
) {

    /**
     * Paging 数据流响应分类切换和排序切换。
     * 衍生自 uiState，确保单一真相源。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val movies: Flow<PagingData<Movie>> = uiState
        .map { it.selectedGenreId to it.sortOrder }
        .distinctUntilChanged()
        .flatMapLatest { (genreId, sort) ->
            if (genreId == null) {
                repository.getPopularMovies()
            } else {
                repository.getMoviesByGenre(genreId, sort)
            }
        }
        .cachedIn(viewModelScope)

    init {
        fetchGenres()
    }

    override fun sendIntent(intent: FilmContract.Intent) {
        when (intent) {
            is FilmContract.Intent.Refresh -> {
                // Paging 刷新通常由 UI 层直接调用 movies.refresh()
            }

            is FilmContract.Intent.SelectGenre -> {
                updateState { copy(selectedGenreId = intent.genreId) }
            }

            is FilmContract.Intent.SelectSortOrder -> {
                updateState { copy(sortOrder = intent.sortOrder) }
            }
        }
    }

    private fun fetchGenres() {
        viewModelScope.launch {
            updateState { copy(isLoadingGenres = true) }
            repository.getMovieGenres().onSuccess { genres ->
                updateState { copy(genres = genres, isLoadingGenres = false) }
            }.onFailure {
                updateState { copy(isLoadingGenres = false) }
            }
        }
    }
}
