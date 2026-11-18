package com.aster.core.localsp

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException

// 同一进程共享一个 DataStore，页面重建不会重复打开偏好文件
private val Context.userPreferences by preferencesDataStore(
    name = "user_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/** 只存储有序来源 ID；来源增删及默认顺序由调用方处理 */
class SourceOrderPreferences(context: Context) {
    private val store = context.applicationContext.userPreferences

    suspend fun readSourceOrder(): List<String> {
        val preferences = store.data.catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }.first()
        val value = preferences[SourceOrderKey] ?: return emptyList()
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
