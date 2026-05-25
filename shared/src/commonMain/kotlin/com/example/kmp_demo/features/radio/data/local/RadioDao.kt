package com.example.kmp_demo.features.radio.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.kmp_demo.features.radio.data.local.model.RadioStationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioDao {
    @Query("SELECT * FROM radio_stations WHERE category = :category AND countryCode = :countryCode ")
    fun getStationsByCategory(
        category: String,
        countryCode: String
    ): PagingSource<Int, RadioStationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<RadioStationEntity>)

    @Query("UPDATE radio_stations SET isFavorite = :isFavorite WHERE stationUuid = :uuid")
    suspend fun updateFavoriteStatus(uuid: String, isFavorite: Boolean)


    @Query("SELECT * FROM radio_stations WHERE isFavorite = 1")
    fun getFavoriteStationsPagingSource(): PagingSource<Int, RadioStationEntity>

    @Query("SELECT * FROM radio_stations ORDER BY lastPlayedTime DESC LIMIT 20")
    fun getRecentStations(): Flow<List<RadioStationEntity>>

    @Query("DELETE FROM radio_stations WHERE category = :category ")
    suspend fun clearRadioByCategory(category: String)

    @Transaction
    suspend fun replaceData(stations: List<RadioStationEntity>, category: String){
        clearRadioByCategory(category)
        insertStations(stations)
    }

}
