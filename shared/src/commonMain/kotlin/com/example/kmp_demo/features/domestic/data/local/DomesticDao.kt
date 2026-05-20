package com.example.kmp_demo.features.domestic.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface DomesticDao {

    /**
     * 按分类获取分页数据源。
     * [typeName] 为分类名称（如"国产剧"、"综艺"），"全部" 表示不过滤。
     */
    @Query(
        """
        SELECT * FROM domestic_media 
        WHERE (:typeName = '全部' OR typeName = :typeName) 
        ORDER BY orderIndex ASC
        """
    )
    fun getMediaPagingSource(typeName: String): PagingSource<Int, DomesticMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: List<DomesticMediaEntity>)

    @Query("DELETE FROM domestic_media WHERE typeName = :typeName")
    suspend fun clearMediaByType(typeName: String)

    @Query("DELETE FROM domestic_media")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceData(typeName: String, media: List<DomesticMediaEntity>) {
        clearMediaByType(typeName)
        insertMedia(media)
    }
}
