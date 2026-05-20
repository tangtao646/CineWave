package com.example.kmp_demo.features.domestic.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.kmp_demo.core.BaseMviViewModel
import com.example.kmp_demo.core.IUiEffect
import com.example.kmp_demo.core.IUiIntent
import com.example.kmp_demo.core.IUiState
import com.example.kmp_demo.core.PageStatus
import com.example.kmp_demo.core.player.domain.EpisodeInfo
import com.example.kmp_demo.core.videosource.domain.VideoSource
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import com.example.kmp_demo.features.domestic.domain.repository.DomesticRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 国内影视详情页 MVI 协议。
 *
 * 分阶段加载：
 * 1. 元数据（封面、简介）→ 快速展示
 * 2. 播放源列表 → 耗时操作，后展示
 */
class DomesticDetailContract {
    data class State(
        val pageStatus: PageStatus = PageStatus.Loading,
        val media: DomesticMedia? = null,
        val videoSources: List<VideoSource> = emptyList(),
        val isSniffing: Boolean = false,
    ) : IUiState

    sealed class Intent : IUiIntent {
        data object Retry : Intent()
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
 * 国内影视详情页 ViewModel。
 *
 * 分阶段加载数据以优化用户体验：
 * - 先加载元数据（封面、简介）→ 立即展示
 * - 再加载播放源列表 → 完成后追加展示
 *
 * 参考 [FilmDetailViewModel] 的 sequential 模式：
 * 先 loadMovieDetail() 展示基本信息，再 sniffVideoSources() 嗅探播放源。
 */
class DomesticDetailViewModel(
    private val repository: DomesticRepository,
    savedStateHandle: SavedStateHandle
) : BaseMviViewModel<DomesticDetailContract.State, DomesticDetailContract.Intent, DomesticDetailContract.Effect>(
    initialState = DomesticDetailContract.State()
) {

    private val mediaTitle: String = checkNotNull(savedStateHandle["title"])

    /**
     * 剧集列表缓存，供播放器页（[DomesticPlayerScreen]）通过共享 ViewModel 读取。
     *
     * 当详情页嗅探到播放源后，自动转换为 [EpisodeInfo] 列表并缓存。
     * 播放器页通过 `koinViewModel()` 获取同一 ViewModel 实例来读取此缓存，
     * 避免将大量剧集数据序列化到导航参数中。
     */
    private val _episodesCache = MutableStateFlow<List<EpisodeInfo>>(emptyList())
    val episodesCache: StateFlow<List<EpisodeInfo>> = _episodesCache.asStateFlow()

    init {
        loadDetail()
    }

    override fun sendIntent(intent: DomesticDetailContract.Intent) {
        when (intent) {
            DomesticDetailContract.Intent.Retry -> loadDetail()
        }
    }

    private fun loadDetail() {
        viewModelScope.launch {
            updateState { copy(pageStatus = PageStatus.Loading) }
            repository.getDetailMeta(mediaTitle)
                .onSuccess { meta ->
                    updateState { copy(pageStatus = PageStatus.Content, media = meta) }
                    sniffVideoSources()
                }
                .onFailure { error ->
                    updateState {
                        copy(pageStatus = PageStatus.Error(error.message))
                    }
                }
        }
    }

    private fun sniffVideoSources() {
        viewModelScope.launch {
            updateState { copy(isSniffing = true) }
            val sources = repository.getDetailSources(mediaTitle)
            updateState { copy(videoSources = sources, isSniffing = false) }
        }
    }

    fun onPlay(source: VideoSource) {
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
                    DomesticDetailContract.Effect.NavigateToPlayer(
                        url = source.url,
                        title = currentState.media?.title ?: mediaTitle,
                        episodes = episodes,
                    )
                )
            } catch (e: Exception) {
                sendEffect(DomesticDetailContract.Effect.ShowToast("播放准备失败: ${e.message}"))
            }
        }
    }
}
