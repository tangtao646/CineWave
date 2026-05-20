package com.example.kmp_demo.core.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "core_remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey val label: String,
    val nextPage: Int?,
    val lastUpdated: Long
)
