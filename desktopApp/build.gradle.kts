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
            packageName = "com.example.kmp_demo"
            packageVersion = "1.0.0"
        }
    }
}
