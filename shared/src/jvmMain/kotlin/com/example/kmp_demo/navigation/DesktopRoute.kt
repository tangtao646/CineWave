package com.example.kmp_demo.navigation

import androidx.navigation3.runtime.NavKey
import com.example.kmp_demo.core.player.domain.EpisodeInfo
import kotlinx.serialization.Serializable

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
    data object RadioPlayer : DesktopRoute
    
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
    ) : DesktopRoute
    
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
    ) : DesktopRoute
}
