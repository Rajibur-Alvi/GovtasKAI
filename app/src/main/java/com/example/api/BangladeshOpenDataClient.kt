package com.example.api

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface BangladeshOpenDataService {
    @GET
    suspend fun getOpenDataRaw(@Url url: String): ResponseBody
}

object BangladeshOpenDataClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://raw.githubusercontent.com/")
        .client(client)
        .build()

    val service: BangladeshOpenDataService = retrofit.create(BangladeshOpenDataService::class.java)
}
