package com.aster.core.localsp

import android.content.Context
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
        val SourceOrderKey = stringPreferencesKey("news_source_order")
    }
}
