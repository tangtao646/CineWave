import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
//    listOf(
//        iosArm64(),
//        iosSimulatorArm64()
//    ).forEach { iosTarget ->
//        iosTarget.binaries.framework {
//            baseName = "Shared"
//            isStatic = true
//        }
//    }
    
    jvm()
    
    androidLibrary {
       namespace = "com.example.kmp_demo.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }


    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.room.runtime)

            // Media3 (Android 音频播放)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.exoplayer.hls)
            implementation(libs.androidx.media3.session)
            implementation(libs.androidx.media3.datasource.okhttp)
            implementation(libs.androidx.media3.ui)

            implementation(libs.guava.android)
            implementation(libs.androidx.paging.runtime)

            implementation(libs.androidx.navigation.common.android)

        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)

            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.encoding)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)

            // app.cash.paging (KMP 跨平台分页)
            implementation(libs.paging.common)
            implementation(libs.paging.compose.common)

            implementation(libs.room.runtime)
            implementation(libs.room.paging)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.kotlinx.datetime)
            implementation(libs.okio)
            implementation(libs.compose.media.player)

            implementation(libs.androidx.navigation.compose)

        }
        jvmMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.room.runtime)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Navigation Compose 在 Desktop 上需要 savedstate 兼容库
            implementation("org.jetbrains.androidx.savedstate:savedstate:1.3.6")
            implementation("org.jetbrains.androidx.savedstate:savedstate-compose:1.3.6")

        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
    generateKotlin = true
}

dependencies {
    // 关键修复：添加 CommonMainMetadata 的 KSP 处理
    add("kspCommonMainMetadata", libs.room.compiler)
    
    // 平台特定的 KSP 处理
    add("kspAndroid", libs.room.compiler)
    // iOS 目标可能不可用（无 Xcode），条件添加
    configurations.matching { it.name.startsWith("kspIos") }.configureEach {
        add(this.name, libs.room.compiler)
    }
    add("kspJvm", libs.room.compiler)

    androidRuntimeClasspath(libs.compose.uiTooling)
}
