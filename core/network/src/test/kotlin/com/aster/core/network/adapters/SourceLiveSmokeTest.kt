package com.aster.core.network.adapters

import com.aster.core.network.NetworkClient
import com.aster.core.model.NetworkError
import com.aster.core.model.NetworkResult
import com.aster.core.network.sources.BuiltInSources
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 显式启用才访问外网；固定样例测试不依赖线上平台可用性。 */
class SourceLiveSmokeTest {
    @Test(timeout = 600_000)
    fun probeConfiguredSources() = runBlocking {
        val selection = System.getenv("THEPRESS_LIVE_SOURCES").orEmpty()
        assumeTrue("设置 THEPRESS_LIVE_SOURCES=all 或逗号分隔的来源 ID 才执行", selection.isNotBlank())
        val sources = BuiltInSources.all.filter { selection == "all" || it.id in selection.split(',') }
        require(sources.isNotEmpty()) { "没有匹配的来源" }
        val directory = File("build/reports/sources").apply { mkdirs() }
        val progress = File(directory, "live-progress.jsonl").apply { writeText("") }
        val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS).apply {
                System.getenv("THEPRESS_LIVE_PROXY")?.takeIf { it.isNotBlank() }?.let {
                    val uri = URI(it)
                    proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, uri.port)))
                }
            }.build()
        val manager = AdapterManager(NetworkClient(http), sources)
        val semaphore = Semaphore(4)
        try {
            val results = sources.map { source -> async {
                semaphore.withPermit {
                    val result = withTimeoutOrNull(45_000) { manager.fetch(source.id) }
                    buildJsonObject {
                        put("id", source.id)
                        put("success", result is NetworkResult.Success)
                        when (result) {
                            is NetworkResult.Success -> put("count", result.data.size)
                            is NetworkResult.Failure -> put("error", when (val error = result.error) {
                                is NetworkError.Http -> "HTTP ${error.code}"
                                is NetworkError.Processing -> "${error.stage}: ${error.message}"
                                is NetworkError.Connection -> "${error.cause.javaClass.simpleName}: ${error.cause.message}"
                                else -> error.javaClass.simpleName
                            })
                            null -> put("error", "抓取超过 45 秒")
                        }
                    }.also { progress.appendText("$it\n") }
                }
            } }.awaitAll()
            File(directory, "live.json").writeText(JsonArray(results).toString())
            assertTrue("部分来源当前不可用，见 ${directory.absolutePath}/live.json", results.all { it["success"].toString() == "true" })
        } finally {
            http.dispatcher.cancelAll()
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdown()
        }
    }
}
