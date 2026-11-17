package com.aster.core.network.adapters.request

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** 每次来源抓取独立创建，避免平台之间、并发抓取之间互相污染 Cookie */
internal class SessionCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > System.currentTimeMillis()) this.cookies.add(cookie)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return cookies.filter { it.matches(url) }
    }
}
