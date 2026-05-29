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
 * 导航板块枚举。
 *
 * 用于侧边导航栏的选中状态判断，按板块分组而非精确路由匹配。
 * 例如 DomesticPlayer 和 DomesticHome 都属于 DOMESTIC 板块，
 * 进入播放器时"国产"按钮仍保持选中状态。
 */
enum class NavSection {
    RADIO,
    FILM,
    DOMESTIC,
}

/**
 * Desktop 端类型安全路由定义 (Navigation3)
 */
@Serializable
sealed interface DesktopRoute : NavKey {
    
    /** 所属导航板块，用于侧边栏选中状态判断 */
    val section: NavSection
    
    @Serializable
    data object RadioList : DesktopRoute {
        override val section: NavSection get() = NavSection.RADIO
    }
    
    @Serializable
    data object RadioSearch : DesktopRoute {
        override val section: NavSection get() = NavSection.RADIO
    }
    
    @Serializable
    data object RadioPlayer : DesktopRoute, FullScreenRoute {
        override val section: NavSection get() = NavSection.RADIO
    }
    
    @Serializable
    data object FilmHome : DesktopRoute {
        override val section: NavSection get() = NavSection.FILM
    }
    
    @Serializable
    data object FilmSearch : DesktopRoute {
        override val section: NavSection get() = NavSection.FILM
    }
    
    @Serializable
    data class FilmDetail(val movieId: Int) : DesktopRoute {
        override val section: NavSection get() = NavSection.FILM
    }
    
    @Serializable
    data class FilmPlayer(
        val url: String, 
        val title: String, 
        val episodes: List<EpisodeInfo> = emptyList()
    ) : DesktopRoute, FullScreenRoute {
        override val section: NavSection get() = NavSection.FILM
    }
    
    @Serializable
    data object DomesticHome : DesktopRoute {
        override val section: NavSection get() = NavSection.DOMESTIC
    }
    
    @Serializable
    data object DomesticSearch : DesktopRoute {
        override val section: NavSection get() = NavSection.DOMESTIC
    }
    
    @Serializable
    data class DomesticDetail(val title: String) : DesktopRoute {
        override val section: NavSection get() = NavSection.DOMESTIC
    }
    
    @Serializable
    data class DomesticPlayer(
        val url: String, 
        val title: String, 
        val episodes: List<EpisodeInfo> = emptyList()
    ) : DesktopRoute, FullScreenRoute {
        override val section: NavSection get() = NavSection.DOMESTIC
    }
}
