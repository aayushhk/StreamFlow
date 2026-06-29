package com.example.data.api

import com.example.data.model.Stream
import retrofit2.http.GET

interface StreamsApi {
    @GET("api/streams.json")
    suspend fun getStreams(): List<Stream>
}
