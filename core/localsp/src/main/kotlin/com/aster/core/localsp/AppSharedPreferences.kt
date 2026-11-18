package com.aster.core.localsp

import android.content.Context
import com.aster.core.model.AppearanceSettings
import com.aster.core.model.ThemeMode
import com.aster.core.model.ThemeColor
import com.aster.core.model.DigestSettings
import com.aster.core.model.DigestDetail
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException

// 同一进程共享一个 DataStore，页面重建不会重复打开偏好文件
private val Context.userPreferences by preferencesDataStore(
    name = "user_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** 应用级偏好存储；业务层通过 core:data 的仓库访问。底层使用 DataStore */
class AppSharedPreferences(context: Context) {
    private val store = context.applicationContext.userPreferences
    private val keyCipher = ApiKeyCipher()

    val digestSettings: Flow<DigestSettings> = store.data.map { preferences ->
        DigestSettings(
            sourceIds = decodeSourceOrder(preferences[DigestSourcesKey]).toSet(),
            model = preferences[DigestModelKey] ?: "deepseek-flash",
            detail = DigestDetail.entries.find { it.name == preferences[DigestDetailKey] } ?: DigestDetail.STANDARD,
            hasApiKey = keyCipher.decrypt(preferences[DigestApiKey]).isNotBlank()
        )
    }.distinctUntilChanged()

    val digestApiKey: Flow<String> = store.data.map { keyCipher.decrypt(it[DigestApiKey]) }

    suspend fun saveDigestSettings(settings: DigestSettings, apiKey: String?) {
        store.edit {
            it[DigestSourcesKey] = JSONArray(settings.sourceIds.sorted()).toString()
            it[DigestModelKey] = settings.model
            it[DigestDetailKey] = settings.detail.name
            if (apiKey != null) it[DigestApiKey] = keyCipher.encrypt(apiKey.trim())
        }
    }

    val appearance: Flow<AppearanceSettings> = store.data.map { preferences ->
        AppearanceSettings(
            mode = ThemeMode.entries.find { it.name == preferences[ThemeModeKey] } ?: ThemeMode.SYSTEM,
            color = ThemeColor.entries.find { it.name == preferences[ThemeColorKey] } ?: ThemeColor.SYSTEM
        )
    }.distinctUntilChanged()

    suspend fun saveThemeMode(mode: ThemeMode) {
        store.edit { it[ThemeModeKey] = mode.name }
    }

    suspend fun saveThemeColor(color: ThemeColor) {
        store.edit { it[ThemeColorKey] = color.name }
    }

    // 读取错误向上传递，让订阅方显式重试，避免发出默认值后永久停止观察
    val sourceOrder: Flow<List<String>> = store.data.map { preferences ->
        decodeSourceOrder(preferences[SourceOrderKey])
    }.distinctUntilChanged()

    private fun decodeSourceOrder(value: String?): List<String> {
        if (value == null) return emptyList()
        return try {
            val array = JSONArray(value)
            (0 until array.length()).mapNotNull { index ->
                (array.opt(index) as? String)?.takeIf { it.isNotBlank() }
            }.distinct()
        } catch (_: JSONException) {
            emptyList()
        }
    }

    suspend fun saveSourceOrder(sourceIds: List<String>) {
        val value = JSONArray(sourceIds.filter { it.isNotBlank() }.distinct()).toString()
        store.edit { it[SourceOrderKey] = value }
    }

    private companion object {
        val DigestSourcesKey = stringPreferencesKey("digest_sources")
        val DigestModelKey = stringPreferencesKey("digest_model")
        val DigestDetailKey = stringPreferencesKey("digest_detail")
        val DigestApiKey = stringPreferencesKey("digest_api_key_encrypted")
        val ThemeModeKey = stringPreferencesKey("theme_mode")
        val ThemeColorKey = stringPreferencesKey("theme_color")
        val SourceOrderKey = stringPreferencesKey("news_source_order")
    }
}
