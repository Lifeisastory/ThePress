package com.aster.core.analytics

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/** Logcat 的唯一出口，所有模块使用相同标签，通过 category 区分日志 */
class LogcatLogger(
    private val minimumLevel: LogLevel = LogLevel.DEBUG,
    private val output: (LogLevel, String) -> Unit = { level, message ->
        val priority = when (level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }
        Log.println(priority, "ThePress", message)
    }
) : Logger {
    override fun log(event: LogEvent) {
        if (event.level < minimumLevel) return
        // 输出故障不应中断 OkHttp 回调或数据加载
        try {
            val parts = splitLogMessage(LogFormatter.format(event))
            if (parts.size == 1) {
                output(event.level, parts.single())
            } else {
                val id = nextOutputId.incrementAndGet()
                parts.forEachIndexed { index, part ->
                    output(event.level, "[event=$id part=${index + 1}/${parts.size}] $part")
                }
            }
        } catch (_: Exception) {
            // 不通过同一个日志出口报告自身故障，避免递归
        }
    }

    private companion object {
        val nextOutputId = AtomicLong()
    }
}

/** 按 UTF-8 字节分段，为 Logcat 元数据留余量，不拆开 emoji 的代理对 */
internal fun splitLogMessage(message: String): List<String> {
    val parts = mutableListOf<String>()
    var start = 0
    var offset = 0
    var bytes = 0
    while (offset < message.length) {
        val codePoint = message.codePointAt(offset)
        val size = when {
            codePoint <= 0x7f -> 1
            codePoint <= 0x7ff -> 2
            codePoint <= 0xffff -> 3
            else -> 4
        }
        if (bytes + size > 3000) {
            parts.add(message.substring(start, offset))
            start = offset
            bytes = 0
        }
        bytes += size
        offset += Character.charCount(codePoint)
    }
    parts.add(message.substring(start))
    return parts
}
