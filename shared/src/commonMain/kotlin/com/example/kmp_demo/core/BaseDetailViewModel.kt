package com.example.kmp_demo.core

import androidx.lifecycle.viewModelScope
import com.example.kmp_demo.core.player.domain.EpisodeInfo
import com.example.kmp_demo.core.videosource.domain.VideoSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 详情页 ViewModel 抽象基类。
 *
 * 封装了通用的播放准备逻辑（[preparePlayback]），
 * 将 [VideoSource] 列表转换为 [EpisodeInfo] 列表并缓存，
 * 供播放器页通过共享 ViewModel 读取。
 *
 * @param S UI 状态类型
 * @param I UI 意图类型
 * @param E UI 副作用类型
 */
abstract class BaseDetailViewModel<S : IUiState, I : IUiIntent, E : IUiEffect>(
    initialState: S
) : BaseMviViewModel<S, I, E>(initialState) {

    /**
     * 剧集列表缓存，供播放器页通过共享 ViewModel 读取。
     *
     * 当详情页嗅探到播放源后，自动转换为 [EpisodeInfo] 列表并缓存。
     * 播放器页通过 `koinViewModel()` 获取同一 ViewModel 实例来读取此缓存，
     * 避免将大量剧集数据序列化到导航参数中。
     */
    private val _episodesCache = MutableStateFlow<List<EpisodeInfo>>(emptyList())
    val episodesCache: StateFlow<List<EpisodeInfo>> = _episodesCache.asStateFlow()

    /**
     * 通用播放准备逻辑。
     *
     * 将 [VideoSource] 列表转换为 [EpisodeInfo] 列表，更新缓存，发送导航 Effect。
     *
     * @param source 用户点击的播放源
     * @param videoSources 完整播放源列表（用于构建剧集列表）
     * @param title 媒体标题
     * @param navigateEffect 导航 Effect 构造器，接收 url、title、episodes 三个参数
     * @param toastEffect Toast Effect 构造器，接收错误消息
     */
    protected fun preparePlayback(
        source: VideoSource,
        videoSources: List<VideoSource>,
        title: String,
        navigateEffect: (url: String, title: String, episodes: List<EpisodeInfo>) -> E,
        toastEffect: (String) -> E,
    ) {
        viewModelScope.launch {
            try {
                val episodes = videoSources.mapIndexed { index, vs ->
                    EpisodeInfo(
                        index = index,
                        label = "第${index + 1}集",
                        url = vs.url,
                        title = vs.quality,
                    )
                }
                _episodesCache.value = episodes
                sendEffect(navigateEffect(source.url, title, episodes))
            } catch (e: Exception) {
                sendEffect(toastEffect("播放准备失败: ${e.message}"))
            }
        }
    }
}
