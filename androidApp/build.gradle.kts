import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid) // 必须应用此插件才能编译 Kotlin 源码 (MainActivity.kt)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.android)
    debugImplementation(libs.compose.uiTooling)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Media3 (Android 视频播放)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)


    // Guava (ListenableFuture)
    implementation(libs.guava.android)
    implementation(libs.paging.common)
    implementation(libs.paging.compose)

    // Sentry 性能监控 (Android App 层)
    implementation(libs.sentry.android)

}

android {
    namespace = "com.example.kmp_demo"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.kmp_demo"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        buildConfig = true
        aidl = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        create("release") {
            val keystoreFileName = project.findProperty("androidKeystoreFile")?.toString()
                ?: "/Users/tangtao/android_project.jks"
            // 如果是绝对路径直接用 file()，否则用 rootProject.file()（相对于项目根目录）
            storeFile = if (keystoreFileName.startsWith("/")) {
                file(keystoreFileName)
            } else {
                rootProject.file(keystoreFileName)
            }
            storePassword = project.findProperty("androidKeystorePassword")?.toString() ?: "123456"
            keyAlias = project.findProperty("androidKeyAlias")?.toString() ?: "key0"
            keyPassword = project.findProperty("androidKeyPassword")?.toString() ?: "123456"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // 强制 JVM 目标版本为 11，与 Java 保持一致，防止 JVM Target 冲突
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}
