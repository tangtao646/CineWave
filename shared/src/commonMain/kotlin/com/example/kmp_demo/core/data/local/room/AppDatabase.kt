package com.example.kmp_demo.core.data.local.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.example.kmp_demo.features.domestic.data.local.DomesticDao
import com.example.kmp_demo.features.domestic.data.local.DomesticMediaEntity
import com.example.kmp_demo.features.film.data.local.FilmDao
import com.example.kmp_demo.features.film.data.local.MovieEntity
import com.example.kmp_demo.features.news.data.local.ArticleEntity
import com.example.kmp_demo.features.news.data.local.NewsDao
import com.example.kmp_demo.features.radio.data.local.RadioDao
import com.example.kmp_demo.features.radio.data.local.model.RadioStationEntity

@Database(
    entities = [
        ArticleEntity::class,
        MovieEntity::class,
        DomesticMediaEntity::class,
        RemoteKeyEntity::class,
        RadioStationEntity::class,
    ],
    version = 9
)
@ConstructedBy(AppDatabaseConstructor::class) // 1. 明确指定构造器
abstract class AppDatabase : RoomDatabase() {
    abstract fun newsDao(): NewsDao
    abstract fun filmDao(): FilmDao
    abstract fun domesticDao(): DomesticDao
    abstract fun radioDao(): RadioDao
    abstract fun remoteKeyDao(): CoreRemoteKeyDao
}

// 2. 定义 expect 构造器对象
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>

expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
        .fallbackToDestructiveMigration()
        .build()
}
