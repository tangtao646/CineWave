import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * 日志开关 — 通过 Gradle property 控制。
 * 本地开发默认 true，CI/CD 打包时通过 -PlogEnabled=false 关闭。
 */
val logEnabled: Boolean = project.findProperty("logEnabled")?.toString()?.toBooleanStrictOrNull() ?: true

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
            jvmTarget = JvmTarget.JVM_1_8
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
            implementation(libs.androidx.media3.datasource)
            implementation(libs.androidx.media3.ui.compose.material3)


            implementation(libs.guava.android)
            implementation(libs.androidx.paging.runtime)

            implementation(libs.androidx.navigation.common.android)

            // Sentry 性能监控 (Android)
            implementation(libs.sentry.android)

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

            // Paging (AndroidX KMP 跨平台分页)
            api(libs.paging.common)
            api(libs.paging.compose)

            implementation(libs.room.runtime)
            implementation(libs.room.paging)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.kotlinx.datetime)
            implementation(libs.okio)
            implementation(libs.compose.media.player)

            implementation(libs.androidx.navigation.compose)

            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.jetbrains.compose.adaptive.navigation3)
            implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)

            // Ktor Server CIO (跨平台 HTTP 服务器，用于本地缓存代理)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.room.runtime)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.compose.ui.util)

            // Paging (AndroidX) — Desktop 端需要显式添加
            implementation(libs.paging.common)
            implementation(libs.paging.compose)
            implementation(libs.androidx.paging.common.desktop)
            implementation(libs.androidx.paging.compose.desktop)
            implementation(libs.androidx.lifecycle.viewmodel.compose.desktop)


            // Navigation Compose 在 Desktop 上需要 savedstate 兼容库
            implementation("org.jetbrains.androidx.savedstate:savedstate:1.3.6")
            implementation("org.jetbrains.androidx.savedstate:savedstate-compose:1.3.6")

            // 需要用户安装 VLC: brew install vlc
            implementation(libs.vlcj)

            // Sentry 性能监控 (JVM/Desktop)
            implementation(libs.sentry.jvm)

        }
    }
}

// ============================================================
// 生成 BuildConfig.kt（现代 KMP 最佳实践，移除了 afterEvaluate）
// ============================================================

// 1. 定义一个标准的独立 Task 类（规范化 Inputs / Outputs）
abstract class GenerateBuildConfigTask : DefaultTask() {
    @get:Input
    abstract val logEnabled: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val dir = outputDirectory.get().asFile
        val outputFile = File(dir, "com/example/kmp_demo/core/BuildConfig.kt")
        outputFile.parentFile.mkdirs()

        outputFile.writeText("""
            package com.example.kmp_demo.core

            /**
             * 日志总开关 — 由 Gradle 构建时注入。
             * 本地开发默认 true，CI/CD 打包时通过 -PlogEnabled=false 关闭。
             */
            const val LOG_ENABLED = ${logEnabled.get()}
        """.trimIndent())
    }
}

// 2. 注册 Task 并利用 Providers 延迟获取 Property
val generateBuildConfig = tasks.register<GenerateBuildConfigTask>("generateBuildConfig") {
    logEnabled.set(
        providers.provider {
            project.findProperty("logEnabled")?.toString()?.toBooleanStrictOrNull() ?: true
        }
    )
    // 自动定位到 build 托管的生成目录
    outputDirectory.set(project.layout.buildDirectory.dir("generated/buildconfig/commonMain"))
}

// 3. 【核心关联】直接注入源码集。
// 这一行在底层会自动为你搞定 compileKotlin / ksp 等所有任务的 dependsOn 关系，无需手动干预。
kotlin.sourceSets.getByName("commonMain") {
    kotlin.srcDir(generateBuildConfig.map { it.outputDirectory })
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
    // jvmMain 需要 Room KSP 来生成 AppDatabase 的 actual 实现（满足 expect 声明）
    add("kspJvm", libs.room.compiler)

    androidRuntimeClasspath(libs.compose.uiTooling)
}
