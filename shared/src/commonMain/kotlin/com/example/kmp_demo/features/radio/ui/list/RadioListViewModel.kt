package com.example.kmp_demo.features.radio.ui.list

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.kmp_demo.core.BaseMviViewModel
import com.example.kmp_demo.core.IUiEffect
import com.example.kmp_demo.core.IUiIntent
import com.example.kmp_demo.core.IUiState
import com.example.kmp_demo.features.radio.data.remote.dto.CountryDto
import com.example.kmp_demo.features.radio.domain.model.RadioStation
import com.example.kmp_demo.features.radio.domain.repository.RadioRepository
import com.example.kmp_demo.features.radio.player.RadioPlayerManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RadioListContract {
    data class State(
        val selectCountryCode: String = "CN",
        val selectCategoryLabel: String = "音乐",
        val categoryMap: Map<String, String> = mapOf(
            "音乐" to "music",
            "故事" to "story",
            "新闻" to "news",
            "收藏" to "favorite",
        ),
        val countries: List<CountryDto> = emptyList()
    ) : IUiState {
        val categoryLabels = categoryMap.keys.toList()
        val selectCategory = if (selectCountryCode != "CN") {
            categoryMap[selectCategoryLabel] ?: "music"
        } else {
            selectCategoryLabel
        }
    }

    sealed class Intent : IUiIntent {
        data object Initial : Intent()
        data class SelectCateIntent(val category: String) : Intent()
        data class ChangeCountry(val countryCode: String) : Intent()
        data class ToggleFavorite(val station: RadioStation) : Intent()
        data class PlayFromList(val stations: List<RadioStation>, val index: Int) : Intent()
        data object TogglePlayPause : Intent()
    }

    sealed class Effect : IUiEffect {
        object ShowCountrySheet : Effect()

    }
}

/**
 * 电台列表 ViewModel - 迁移至 Koin
 */
class RadioListViewModel(
    private val repository: RadioRepository,
    val playerManager: RadioPlayerManager
) : BaseMviViewModel<RadioListContract.State, RadioListContract.Intent, RadioListContract.Effect>(
    initialState = RadioListContract.State()
) {

    // 组合国家和分类，观察数据库变化
    @OptIn(ExperimentalCoroutinesApi::class)
    val stations: Flow<PagingData<RadioStation>> = uiState.flatMapLatest { state ->
        if (state.selectCategory == "收藏") {
            repository.getFavoriteStations()
        } else {
            repository.getStations(state.selectCategory, state.selectCountryCode)
        }
    }.cachedIn(viewModelScope)


    init {
        viewModelScope.launch {
            // 获取初始位置
            try {
                val countryCode = repository.getCurrentCountryCode()
                val countries = repository.getCountries()
                updateState {
                    copy(selectCountryCode = countryCode, countries = countries)
                }
            } catch (e: Exception) {
                // 打印错误，也可发送 Effect 提示用户
                e.printStackTrace()
            }

        }

    }


    override fun sendIntent(intent: RadioListContract.Intent) {
        when (intent) {
            is RadioListContract.Intent.Initial -> {}
            is RadioListContract.Intent.SelectCateIntent -> {
                updateState { copy(selectCategoryLabel = intent.category) }
            }

            is RadioListContract.Intent.ChangeCountry -> {
                updateState { copy(selectCountryCode = intent.countryCode) }
            }

            is RadioListContract.Intent.ToggleFavorite -> {
                viewModelScope.launch {
                    repository.toggleFavorite(intent.station.uuid, !intent.station.isFavorite)
                }
            }

            is RadioListContract.Intent.PlayFromList -> {
                playerManager.playFromList(intent.stations, intent.index)
            }

            is RadioListContract.Intent.TogglePlayPause -> {
                playerManager.togglePlayPause()
            }
        }
    }
}
