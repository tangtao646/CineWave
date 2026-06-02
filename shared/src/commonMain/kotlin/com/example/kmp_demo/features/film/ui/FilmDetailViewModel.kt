package com.example.kmp_demo.features.film.ui

import androidx.lifecycle.viewModelScope
import com.example.kmp_demo.core.BaseDetailViewModel
import com.example.kmp_demo.core.IUiEffect
import com.example.kmp_demo.core.IUiIntent
import com.example.kmp_demo.core.IUiState
import com.example.kmp_demo.core.PageStatus
import com.example.kmp_demo.core.player.domain.EpisodeInfo
import com.example.kmp_demo.core.videosource.domain.VideoSource
import com.example.kmp_demo.features.film.domain.model.MovieDetail
import com.example.kmp_demo.features.film.domain.repository.FilmRepository
import kotlinx.coroutines.launch

/**
 * 电影详情页 MVI 协议
 */
class FilmDetailContract {
    data class State(
        val pageStatus: PageStatus = PageStatus.Loading,
        val movie: MovieDetail? = null,
        val videoSources: List<VideoSource> = emptyList(),
        val isSniffing: Boolean = false
    ) : IUiState

    sealed class Intent : IUiIntent {
        data object Retry : Intent()
        data object SniffSources : Intent()
        data class PlayVideo(val source: VideoSource) : Intent()
    }

    sealed class Effect : IUiEffect {
        data class NavigateToPlayer(
            val url: String,
            val title: String,
            val episodes: List<EpisodeInfo> = emptyList(),
        ) : Effect()
        data class ShowToast(val message: String) : Effect()
    }
}

/**
 * 电影详情页 ViewModel
 *
 * 跨平台通用，不依赖任何平台特定类（如 SavedStateHandle）。
 * movieId 由各平台通过构造函数直接传入：
 * - Android：从 Navigation Compose 的 SavedStateHandle 提取后传入
 * - Desktop：从导航参数直接传入
 */
class FilmDetailViewModel(
    private val repository: FilmRepository,
    private val movieId: Int,
) : BaseDetailViewModel<FilmDetailContract.State, FilmDetailContract.Intent, FilmDetailContract.Effect>(
    initialState = FilmDetailContract.State()
) {

    init {
        loadMovieDetail()
    }

    override fun sendIntent(intent: FilmDetailContract.Intent) {
        when (intent) {
            FilmDetailContract.Intent.Retry -> loadMovieDetail()
            FilmDetailContract.Intent.SniffSources -> sniffVideoSources()
            is FilmDetailContract.Intent.PlayVideo -> handlePlay(intent.source)
        }
    }

    private fun handlePlay(source: VideoSource) {
        preparePlayback(
            source = source,
            videoSources = currentState.videoSources,
            title = currentState.movie?.title ?: "",
            navigateEffect = { url, title, episodes ->
                FilmDetailContract.Effect.NavigateToPlayer(url, title, episodes)
            },
            toastEffect = { message ->
                FilmDetailContract.Effect.ShowToast(message)
            }
        )
    }

    private fun loadMovieDetail() {
        viewModelScope.launch {
            updateState { copy(pageStatus = PageStatus.Loading) }
            repository.getMovieDetail(movieId)
                .onSuccess { detail ->
                    updateState { copy(pageStatus = PageStatus.Content, movie = detail) }
                    sniffVideoSources()
                }
                .onFailure { error ->
                    updateState { copy(pageStatus = PageStatus.Error(error.message)) }
                }
        }
    }

    private fun sniffVideoSources() {
        val currentMovie = currentState.movie ?: return
        viewModelScope.launch {
            updateState { copy(isSniffing = true) }
            val sources = repository.searchVideoSources(title = currentMovie.title)
            updateState { copy(videoSources = sources, isSniffing = false) }
        }
    }
}
