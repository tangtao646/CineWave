package com.example.kmp_demo.core.data.local

import androidx.paging.PagingSource

interface BaseLocalDataSource<T : Any, K: Any> {
    suspend fun insert(data: List<T>, key: K)
    fun getPagingSource(key: K): PagingSource<Int, T>
    suspend fun clear(key: K)
    suspend fun replaceData(data: List<T>, key: K)
    suspend fun getNextPage(key: K): Int?
    suspend fun saveNextPage(key: K, nextPage: Int?)
    suspend fun getLastUpdated(key: K): Long
}
