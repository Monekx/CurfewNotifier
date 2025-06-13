package com.monekx.curfewnotifier.network

import com.monekx.curfewnotifier.data.RssFeed
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface NewsApiService {
    @GET
    suspend fun getRssFeed(@Url url: String): Response<RssFeed>
}