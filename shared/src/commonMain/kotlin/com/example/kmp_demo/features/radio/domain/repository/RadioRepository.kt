package com.example.kmp_demo.features.radio.domain.repository

import androidx.paging.PagingData
import com.example.kmp_demo.features.radio.data.remote.dto.CountryDto
import com.example.kmp_demo.features.radio.domain.model.RadioStation
import kotlinx.coroutines.flow.Flow

interface RadioRepository {
    fun getStations(category: String, countryCode: String): Flow<PagingData<RadioStation>>
    suspend fun searchStations(query: String, countryCode: String?): List<RadioStation>
    suspend fun toggleFavorite(uuid: String, isFavorite: Boolean)
    fun getFavoriteStations(): Flow<PagingData<RadioStation>>
    suspend fun getCurrentCountryCode(): String
    suspend fun getCountries(): List<CountryDto>
}
