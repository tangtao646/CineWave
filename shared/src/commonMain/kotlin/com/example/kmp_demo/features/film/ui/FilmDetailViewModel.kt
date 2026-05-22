package com.example.kmp_demo.features.film.ui

import androidx.lifecycle.viewModelScope
import com.example.kmp_demo.core.*
import com.example.kmp_demo.core.player.domain.EpisodeInfo
import com.example.kmp_demo.features.film.domain.model.MovieDetail
import com.example.kmp_demo.features.film.domain.model.VideoSource
import com.example.kmp_demo.features.film.domain.repository.FilmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : BaseMviViewModel<FilmDetailContract.State, FilmDetailContract.Intent, FilmDetailContract.Effect>(
    initialState = FilmDetailContract.State()
) {

    /**
     * 剧集列表缓存，供播放器页通过共享 ViewModel 读取。
     *
     * 当详情页嗅探到播放源后，自动转换为 [EpisodeInfo] 列表并缓存。
     * 播放器页通过 `koinViewModel()` 获取同一 ViewModel 实例来读取此缓存，
     * 避免将大量剧集数据序列化到导航参数中。
     */
    private val _episodesCache = MutableStateFlow<List<EpisodeInfo>>(emptyList())
    val episodesCache: StateFlow<List<EpisodeInfo>> = _episodesCache.asStateFlow()

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
        viewModelScope.launch {
            try {
                // 将 VideoSource 列表转换为 EpisodeInfo 列表
                val episodes = currentState.videoSources.mapIndexed { index, vs ->
                    EpisodeInfo(
                        index = index,
                        label = "第${index + 1}集",
                        url = vs.url,
                        title = vs.quality,
                    )
                }
                // 更新缓存，供播放器页通过共享 ViewModel 读取
                _episodesCache.value = episodes

                sendEffect(
                    FilmDetailContract.Effect.NavigateToPlayer(
                        url = source.url,
                        title = currentState.movie?.title ?: "",
                        episodes = episodes,
                    )
                )
            } catch (e: Exception) {
                sendEffect(FilmDetailContract.Effect.ShowToast("播放准备失败: ${e.message}"))
            }
        }
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
