package com.example.kmp_demo.features.domestic.data.local

import androidx.paging.PagingSource
import com.example.kmp_demo.core.data.local.room.BaseLocalDataSourceImpl
import com.example.kmp_demo.core.data.local.room.CoreRemoteKeyDao

/**
 * 国内影视本地数据源。
 *
 * 封装 [DomesticDao] 和 [CoreRemoteKeyDao]，提供分页缓存能力。
 * key 格式：domestic_{typeName}（如 domestic_国产剧、domestic_全部）
 */
class DomesticLocalDataSource(
    private val domesticDao: DomesticDao,
    remoteKeyDao: CoreRemoteKeyDao,
) : BaseLocalDataSourceImpl<DomesticMediaEntity, String>(remoteKeyDao, "domestic") {

    override suspend fun insert(data: List<DomesticMediaEntity>, key: String) {
        domesticDao.insertMedia(data)
    }


    override fun getPagingSource(key: String): PagingSource<Int, DomesticMediaEntity> {
        // key 格式为 "domestic_{typeName}"，提取 typeName
        val typeName = key.removePrefix("domestic_")
        return domesticDao.getMediaPagingSource(typeName)
    }

    override suspend fun clearData(key: String) {
        val typeName = key.removePrefix("domestic_")
        domesticDao.clearMediaByType(typeName)
    }

    override suspend fun replaceData(data: List<DomesticMediaEntity>, key: String) {
        val typeName = key.removePrefix("domestic_")
        domesticDao.replaceData(typeName, data)
    }
}
