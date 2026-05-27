package com.example.kmp_demo.features.film.data.remote

import cinewave.shared.generated.resources.Res
import org.jetbrains.compose.resources.InternalResourceApi

/**
 * TMDB API Key 提供者。
 *
 * 从 Compose Resources 的 [files/tmdb_api_key.txt] 读取 API Key。
 * 与项目中 [db.json] 的注入方式完全一致，确保跨平台统一：
 *
 * - **本地开发**：手动创建 `shared/src/commonMain/composeResources/files/tmdb_api_key.txt`
 * - **CI/CD**：通过 GitHub Secrets 自动注入（与 DB_JSON 同一套机制）
 *
 * 使用方式：
 * ```bash
 * # 本地开发：创建 API Key 文件
 * echo "your_tmdb_api_key" > shared/src/commonMain/composeResources/files/tmdb_api_key.txt
 * ```
 *
 * @see ComposeResourceSiteConfigProvider 相同的模式用于读取 db.json
 */
class ApiKeyProvider {

    /**
     * 获取 TMDB API Key。
     *
     * @throws IllegalStateException 如果 [tmdb_api_key.txt] 文件不存在或为空
     */
    @OptIn(InternalResourceApi::class)
    suspend fun getApiKey(): String {
        val bytes = Res.readBytes("files/tmdb_api_key.txt")
        val key = bytes.decodeToString().trim()

        require(key.isNotBlank()) {
            """
            |❌ TMDB API Key 未配置！
            |
            |请创建文件：shared/src/commonMain/composeResources/files/tmdb_api_key.txt
            |并在其中写入你的 TMDB API Key。
            |
            |该文件已被 .gitignore 排除，不会提交到远程仓库。
            |CI/CD 环境中会自动从 GitHub Secrets 注入。
            """.trimMargin()
        }

        return key
    }
}
