import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.compose.uiToolingPreview)

    // Koin DI
    implementation(libs.koin.core)
    implementation(libs.androidx.navigation.common.desktop)


    // Navigation Compose 在 Desktop 上需要 savedstate 兼容库
    implementation("org.jetbrains.androidx.savedstate:savedstate:1.3.6")
    implementation("org.jetbrains.androidx.savedstate:savedstate-compose:1.3.6")
    implementation(libs.androidx.lifecycle.viewmodel.compose.desktop)
    implementation(libs.androidx.paging.common.desktop)
    implementation(libs.androidx.paging.compose.desktop)
}

compose.desktop {
    application {
        mainClass = "com.example.kmp_demo.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "CineWave"
            packageVersion = "1.0.0"

            description = "CineWave - 跨平台影音播放应用"
            vendor = "CineWave"

            // macOS 配置
            macOS {
                bundleID = "com.example.kmp_demo"
                iconFile.set(project.file("src/main/resources/icons/icon.icns"))
            }

            // Windows 配置
            windows {
                menuGroup = "CineWave"
                upgradeUuid = "c4e5f6a7-b8c9-4d0e-a1f2-3b4c5d6e7f80"
                iconFile.set(project.file("src/main/resources/icons/icon.ico"))
            }

            // Linux 配置
            linux {
                packageName = "cinewave"
                iconFile.set(project.file("src/main/resources/icons/icon.png"))
            }

            appResourcesRootDir.set(layout.projectDirectory.dir("src/main/resources"))
        }

        // ProGuard 配置 - 防止反射调用的类被混淆移除
        buildTypes {
            release {
                proguard {
                    configurationFiles.from(project.file("proguard-rules.pro"))
                }
            }
        }
    }
}