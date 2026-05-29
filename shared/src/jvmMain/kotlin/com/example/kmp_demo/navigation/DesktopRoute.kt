package com.example.kmp_demo.navigation

import androidx.navigation3.runtime.NavKey
import com.example.kmp_demo.core.player.domain.EpisodeInfo
import kotlinx.serialization.Serializable

/**
 * 全屏路由标记接口。
 *
 * 实现了此接口的路由会在进入时自动隐藏侧边导航栏，
 * 实现沉浸式全屏体验（播放器等场景）。
 * 新增需要全屏的路由只需让对应类实现此接口即可，
 * 无需修改 App.jvm.kt 中的判断逻辑。
 */
interface FullScreenRoute : DesktopRoute

/**
 * Desktop 端类型安全路由定义 (Navigation3)
 */
@Serializable
sealed interface DesktopRoute : NavKey {
    
    @Serializable
    data object RadioList : DesktopRoute
    
    @Serializable
    data object RadioSearch : DesktopRoute
    
    @Serializable
    data object RadioPlayer : DesktopRoute, FullScreenRoute
    
    @Serializable
    data object FilmHome : DesktopRoute
    
    @Serializable
    data object FilmSearch : DesktopRoute
    
    @Serializable
    data class FilmDetail(val movieId: Int) : DesktopRoute
    
    @Serializable
    data class FilmPlayer(
        val url: String, 
        val title: String, 
        val episodes: List<EpisodeInfo> = emptyList()
    ) : DesktopRoute, FullScreenRoute
    
    @Serializable
    data object DomesticHome : DesktopRoute
    
    @Serializable
    data object DomesticSearch : DesktopRoute
    
    @Serializable
    data class DomesticDetail(val title: String) : DesktopRoute
    
    @Serializable
    data class DomesticPlayer(
        val url: String, 
        val title: String, 
        val episodes: List<EpisodeInfo> = emptyList()
    ) : DesktopRoute, FullScreenRoute
}
