package com.example.data.repository

import com.example.data.api.StreamsApi
import com.example.data.db.StreamDao
import com.example.data.model.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class StreamRepository(
    private val streamDao: StreamDao
) {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://iptv-org.github.io/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val api = retrofit.create(StreamsApi::class.java)

    suspend fun getStreamsCount(): Int {
        return withContext(Dispatchers.IO) {
            streamDao.getStreamsCount()
        }
    }

    suspend fun refreshStreams(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val streams = api.getStreams()
                if (streams.isNotEmpty()) {
                    streamDao.clearStreams()
                    streamDao.insertStreams(streams)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun loadStreams(query: String): List<Stream> {
        return withContext(Dispatchers.IO) {
            if (query.isBlank()) {
                streamDao.getAllStreams()
            } else {
                val dbQuery = "%${query.trim()}%"
                streamDao.searchStreams(dbQuery)
            }
        }
    }
}
