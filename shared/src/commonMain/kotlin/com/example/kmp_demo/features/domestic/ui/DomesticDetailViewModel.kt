package com.example.kmp_demo.features.domestic.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.kmp_demo.core.BaseDetailViewModel
import com.example.kmp_demo.core.IUiEffect
import com.example.kmp_demo.core.IUiIntent
import com.example.kmp_demo.core.IUiState
import com.example.kmp_demo.core.PageStatus
import com.example.kmp_demo.core.player.domain.EpisodeInfo
import com.example.kmp_demo.core.videosource.domain.VideoSource
import com.example.kmp_demo.features.domestic.domain.model.DomesticMedia
import com.example.kmp_demo.features.domestic.domain.repository.DomesticRepository
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
        data class Init(val title: String) : Intent()
        data object Retry : Intent()
        data class Play(val source: VideoSource) : Intent()
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
 * 跨平台通用，不依赖任何平台特定类（如 SavedStateHandle）。
 * mediaTitle 由各平台通过构造函数直接传入：
 * - Android：从 Navigation Compose 的 SavedStateHandle 提取后传入
 * - Desktop：从导航参数直接传入
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
    val mediaTitle: String
) : BaseDetailViewModel<DomesticDetailContract.State, DomesticDetailContract.Intent, DomesticDetailContract.Effect>(
    initialState = DomesticDetailContract.State()
) {

    private var currentMediaTitle: String = mediaTitle

    override fun sendIntent(intent: DomesticDetailContract.Intent) {
        when (intent) {
            is DomesticDetailContract.Intent.Init -> handleInit(intent.title)
            DomesticDetailContract.Intent.Retry -> loadDetail(currentMediaTitle)
            is DomesticDetailContract.Intent.Play -> handlePlay(intent.source)
        }
    }

    private fun handleInit(targetTitle: String) {
        //  核心防御：如果切进来的 title 已经在展示了，并且已经有内容，直接复用，不重复请求
        if (currentMediaTitle == targetTitle && currentState.pageStatus is PageStatus.Content) {
            return
        }

        // 说明切换了不同的 item (或者强杀重建后重新激活)
        currentMediaTitle = targetTitle
        loadDetail(targetTitle)
    }

    private fun loadDetail(title: String) {
        viewModelScope.launch {
            updateState { copy(pageStatus = PageStatus.Loading) }
            repository.getDetailMeta(title)
                .onSuccess { meta ->
                    updateState { copy(pageStatus = PageStatus.Content, media = meta) }
                    sniffVideoSources(title)
                }
                .onFailure { error ->
                    updateState {
                        copy(pageStatus = PageStatus.Error(error.message))
                    }
                }
        }
    }

    private fun sniffVideoSources(title: String) {
        viewModelScope.launch {
            updateState { copy(isSniffing = true) }
            val sources = repository.getDetailSources(title)
            updateState { copy(videoSources = sources, isSniffing = false) }
        }
    }

    private fun handlePlay(source: VideoSource) {
        preparePlayback(
            source = source,
            videoSources = currentState.videoSources,
            title = currentState.media?.title ?: currentMediaTitle,
            navigateEffect = DomesticDetailContract.Effect::NavigateToPlayer,
            toastEffect = DomesticDetailContract.Effect::ShowToast
        )
    }
}
