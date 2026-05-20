package com.example.kmp_demo.core.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Android 平台上的 Application Context 持有者
 * 需要在 Application.onCreate() 或 MainActivity.onCreate() 中初始化
 */
object AndroidAppContext {
    lateinit var context: Context
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
    }
}

// 注意：开启 generateKotlin = true 后，Room KSP 会自动生成 AppDatabaseConstructor 的 actual 实现。
// 手动编写 actual object 会导致 "The @ConstructedBy definition must be an 'expect' declaration" 编译错误。

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder(
        AndroidAppContext.context,
        AppDatabase::class.java,
        "study_demo.db"
    )
}
