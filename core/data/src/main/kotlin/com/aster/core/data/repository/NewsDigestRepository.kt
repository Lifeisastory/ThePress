package com.aster.core.data.repository

import com.aster.core.model.DigestGenerationResult
import com.aster.core.model.NewsDigest
import kotlinx.coroutines.flow.Flow

interface NewsDigestRepository {
    fun observeLatestDigest(): Flow<NewsDigest?>
    suspend fun generateDigest(): DigestGenerationResult
}
