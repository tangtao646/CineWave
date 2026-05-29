package com.example.kmp_demo.core.player.ui

import com.example.kmp_demo.core.BaseMviViewModel
import com.example.kmp_demo.core.IUiEffect
import com.example.kmp_demo.core.IUiIntent
import com.example.kmp_demo.core.IUiState
import com.example.kmp_demo.core.player.domain.EpisodeInfo
import com.example.kmp_demo.core.player.domain.VideoPlayerManager

/**
 * 播放器页 MVI 协议。
 *
 * 管理剧集索引、选集弹窗等播放器业务逻辑，
 * 消除 [DomesticPlayerScreen] 和 [FilmPlayerScreen] 中的重复代码。
 */
class PlayerContract {
    /**
     * 播放器 UI 状态。
     *
     * 所有 UI 所需的状态集中在此，Screen 层只需订阅 [uiState]。
     *
     * @param currentIndex 当前播放的剧集索引
     * @param episodes 剧集列表
     * @param seriesTitle 剧集系列名称（如"狂飙"）
     * @param showEpisodeSheet 是否显示选集弹窗
     */
    data class State(
        val currentIndex: Int = 0,
        val episodes: List<EpisodeInfo> = emptyList(),
        val seriesTitle: String = "",
        val showEpisodeSheet: Boolean = false,
    ) : IUiState {
        /** 当前播放的 URL（由 currentIndex 派生） */
        val currentUrl: String
            get() = episodes.getOrNull(currentIndex)?.url ?: ""

        /** 显示标题（如"狂飙 · 第3集"），由 seriesTitle 和当前剧集派生 */
        val displayTitle: String
            get() {
                val episode = episodes.getOrNull(currentIndex)
                return if (episode != null) {
                    "$seriesTitle · ${episode.label}"
                } else {
                    seriesTitle
                }
            }

        /** 当前剧集标签（如"第3集"），供控制栏显示 */
        val currentEpisodeLabel: String?
            get() = episodes.getOrNull(currentIndex)?.label
    }

    sealed class Intent : IUiIntent {
        /** 初始化剧集上下文 */
        data class Init(
            val episodes: List<EpisodeInfo>,
            val initialIndex: Int,
            val seriesTitle: String,
        ) : Intent()

        /** 切换剧集 */
        data class SelectEpisode(val index: Int) : Intent()

        /** 打开选集弹窗 */
        data object OpenEpisodeSheet : Intent()

        /** 关闭选集弹窗 */
        data object DismissEpisodeSheet : Intent()
    }

    sealed class Effect : IUiEffect
}

/**
 * 通用播放器 ViewModel。
 *
 * 职责：
 * 1. 管理剧集索引 [currentIndex] 的维护与更新
 * 2. 通过 [onManagerCreated] 回调与 [VideoPlayerManager] 建立连接
 * 3. 选集弹窗状态管理
 *
 * 所有 UI 状态通过 [uiState] 统一暴露，Screen 层只需订阅一个 StateFlow。
 *
 * 使用方式：
 * ```kotlin
 * val viewModel: PlayerViewModel = koinViewModel()
 * viewModel.sendIntent(PlayerContract.Intent.Init(episodes, initialIndex, seriesTitle))
 * ```
 */
class PlayerViewModel : BaseMviViewModel<PlayerContract.State, PlayerContract.Intent, PlayerContract.Effect>(
    initialState = PlayerContract.State()
) {
    /** VideoPlayerManager 引用，通过 onManagerCreated 回调获取 */
    private var manager: VideoPlayerManager? = null

    /**
     * 供 [PlatformVideoPlayerScreen] 使用的 onManagerCreated 回调。
     *
     * 当 PlatformVideoPlayerScreen 创建 Manager 后调用此回调，
     * ViewModel 借此获取 Manager 引用。
     */
    val onManagerCreated: (VideoPlayerManager) -> Unit = { mgr ->
        manager = mgr
    }

    override fun sendIntent(intent: PlayerContract.Intent) {
        when (intent) {
            is PlayerContract.Intent.Init -> handleInit(intent)
            is PlayerContract.Intent.SelectEpisode -> handleSelectEpisode(intent.index)
            is PlayerContract.Intent.OpenEpisodeSheet -> {
                updateState { copy(showEpisodeSheet = true) }
            }
            is PlayerContract.Intent.DismissEpisodeSheet -> {
                updateState { copy(showEpisodeSheet = false) }
            }
        }
    }

    private fun handleInit(intent: PlayerContract.Intent.Init) {
        val initialIndex = intent.initialIndex.coerceIn(0, intent.episodes.lastIndex.coerceAtLeast(0))
        updateState {
            copy(
                episodes = intent.episodes,
                currentIndex = initialIndex,
                seriesTitle = intent.seriesTitle,
            )
        }
    }

    private fun handleSelectEpisode(index: Int) {
        updateState {
            copy(
                currentIndex = index,
                showEpisodeSheet = false,
            )
        }
        // URL 变化会触发 PlatformVideoPlayerScreen 内部的 LaunchedEffect(url) 自动切换
    }
}
