package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.Stream

@Dao
interface StreamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreams(streams: List<Stream>)

    @Query("DELETE FROM streams")
    suspend fun clearStreams()

    @Query("SELECT COUNT(*) FROM streams")
    suspend fun getStreamsCount(): Int

    @Query("SELECT * FROM streams ORDER BY title ASC")
    suspend fun getAllStreams(): List<Stream>

    @Query("SELECT * FROM streams WHERE title LIKE :query OR url LIKE :query ORDER BY title ASC")
    suspend fun searchStreams(query: String): List<Stream>
}
