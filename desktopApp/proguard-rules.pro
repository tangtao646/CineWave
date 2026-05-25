# Paging - 保留反射调用的类
-keep class androidx.paging.** { *; }
# 保留 PagingDataDiffer 本身（app.cash.paging 在 JVM 上委托给 androidx.paging.compose.LazyPagingItems）
-keep class androidx.paging.PagingDataDiffer { *; }
-keep class * extends androidx.paging.PagingDataDiffer { *; }
-keepclassmembers class * extends androidx.paging.PagingDataDiffer {
    *;
}

# Compose - 保留 Compose 运行时类
-keep class androidx.compose.** { *; }

# Koin - 保留 DI 相关类
-keep class org.koin.** { *; }
