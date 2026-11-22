package com.vegam.engine

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

/**
 * Extra request headers (User-Agent, Referer, Cookie) jo kisi page se pakde gaye download ko chahiye hote hain
 * (file-host pages ka link aksar usi browser session/cookies se bandha hota hai).
 * DB mein text ("Naam: value" har line) ki tarah save hota hai.
 */
object RequestHeaders {
    /** Sirf ye headers allowed: engine ke apne (Range, Accept-Encoding, Host...) kabhi override nahi hote. */
    private val ALLOWED = setOf("user-agent", "referer", "cookie", "accept-language")

    fun encode(headers: Map<String, String>): String? =
        headers.filter { (k, v) -> k.lowercase() in ALLOWED && v.isNotBlank() && '\n' !in v && '\r' !in v }
            .entries.joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { null }

    fun decode(text: String?): Map<String, String> =
        text.orEmpty().lines().mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }.filter { (k, v) -> k.lowercase() in ALLOWED && v.isNotEmpty() }.toMap()

    /**
     * Har request (redirect hop bhi) par headers lagao. Cookie sirf us host ko jaati hai jahan se pakdi gayi,
     * kisi doosre host par redirect ho to nahi (cookie leak na ho).
     */
    fun apply(client: OkHttpClient, originalUrl: String, headers: Map<String, String>): OkHttpClient {
        if (headers.isEmpty()) return client
        val host = originalUrl.toHttpUrlOrNull()?.host
        return client.newBuilder().addNetworkInterceptor { chain ->
            val req = chain.request()
            val b = req.newBuilder()
            headers.forEach { (k, v) ->
                // OkHttp apna default User-Agent pehle hi laga deta hai: usse badalna hai. Baaki headers khaali ho tabhi.
                if (!k.equals("user-agent", ignoreCase = true) && req.header(k) != null) return@forEach
                if (k.equals("cookie", ignoreCase = true) && req.url.host != host) return@forEach
                b.header(k, v)
            }
            chain.proceed(b.build())
        }.build()
    }
}
