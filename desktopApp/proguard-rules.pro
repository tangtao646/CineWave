# Paging - 保留反射调用的类
-keep class androidx.paging.** { *; }
-keep class * extends androidx.paging.PagingDataDiffer { *; }
-keepclassmembers class * extends androidx.paging.PagingDataDiffer {
    *;
}

# Compose - 保留 Compose 运行时类
-keep class androidx.compose.** { *; }

# Koin - 保留 DI 相关类
-keep class org.koin.** { *; }
