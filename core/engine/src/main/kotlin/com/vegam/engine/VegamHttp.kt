package com.vegam.engine

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/** Tuned OkHttp client sirf downloads ke liye. */
object VegamHttp {
    fun newClient(): OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 16 })
        // DHYAN: HTTP/2 mein ek host ke saare segments ek hi TCP connection par multiplex ho jaate hain,
        // to "8 connections" asal mein 1 ban jaata hai. Speed ke liye HTTP/1.1 (alag TCP connection
        // har segment ko) default rakha hai. Real servers par dono ka speed test karna padega.
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()
}
