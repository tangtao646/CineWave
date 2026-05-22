package com.example.kmp_demo.core.data.local.room

import androidx.room.RoomDatabase

/**
 * Desktop 端 [getDatabaseBuilder] 的 actual 实现。
 *
 * Desktop 端不使用 Room 数据库，但 commonMain 中定义了 expect 声明，
 * 因此需要提供 actual 实现以满足编译要求。
 *
 * 此实现不会被实际调用，因为 Desktop 的 DI 模块（*ModuleJvm）不注册 Room。
 */
actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    throw UnsupportedOperationException("Desktop 端不使用 Room 数据库")
}
