package com.example.kmp_demo.core.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CoreRemoteKeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveKey(key: RemoteKeyEntity)

    @Query("SELECT * FROM core_remote_keys WHERE label = :label")
    suspend fun getKey(label: String): RemoteKeyEntity?

    @Query("DELETE FROM core_remote_keys WHERE label = :label")
    suspend fun clearKey(label: String)
}
