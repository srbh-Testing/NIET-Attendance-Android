package com.example.nietattendance

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("cookie_store", Context.MODE_PRIVATE)
    private val cookies = mutableMapOf<String, MutableList<Cookie>>()

    init {
        val saved = prefs.getStringSet("cookies", emptySet()) ?: emptySet()
        for (raw in saved) {
            val parts = raw.split("|", limit = 2)
            if (parts.size == 2) {
                val host = parts[0]
                val url = HttpUrl.Builder().scheme("https").host(host).build()
                val cookie = Cookie.parse(url, parts[1])
                if (cookie != null) {
                    cookies.getOrPut(host) { mutableListOf() }.add(cookie)
                }
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookieList: List<Cookie>) {
        val host = url.host
        val existing = cookies.getOrPut(host) { mutableListOf() }
        for (newCookie in cookieList) {
            existing.removeAll { it.name == newCookie.name }
            existing.add(newCookie)
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookies[url.host] ?: emptyList()
    }

    fun clear() {
        cookies.clear()
        prefs.edit().remove("cookies").apply()
    }

    private fun persist() {
        val flat = mutableSetOf<String>()
        for ((host, list) in cookies) {
            for (c in list) {
                flat.add("$host|${c}")
            }
        }
        prefs.edit().putStringSet("cookies", flat).apply()
    }
}
