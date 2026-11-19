package com.aster.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DigestDetail { BRIEF, STANDARD, DETAILED }

data class DigestSettings(
    val sourceIds: Set<String> = emptySet(),
    val model: String = "deepseek-flash",
    val detail: DigestDetail = DigestDetail.STANDARD,
    val hasApiKey: Boolean = false
)

@Serializable
data class DigestReference(
    val id: String,
    val sourceId: String,
    val sourceName: String,
    val articleId: String,
    val title: String,
    val summary: String?,
    val url: String
)

@Serializable
data class DigestItem(
    val title: String,
    val body: String,
    val references: List<DigestReference>,
    val isOriginal: Boolean = false
)

@Serializable
data class DigestSourceStatus(val id: String, val name: String, val articleCount: Int, val refreshFailed: Boolean)

@Serializable
data class NewsDigest(
    val generatedAt: Long,
    val model: String,
    val detail: DigestDetail,
    val sources: List<DigestSourceStatus>,
    val inputCount: Int,
    val items: List<DigestItem>
)

@Serializable
enum class DigestFailure {
    NOT_CONFIGURED, NO_SOURCES, NO_NEWS, INPUT_TOO_LARGE, BUSY,
    CONNECTION, TIMEOUT, AUTHENTICATION, BALANCE, RATE_LIMIT, SERVER,
    INVALID_RESPONSE, STORAGE, STEP_LIMIT
}

sealed interface DigestGenerationResult {
    data object Success : DigestGenerationResult
    data class Failure(val reason: DigestFailure) : DigestGenerationResult
}
