package com.example.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.model.Stream

@Database(entities = [Stream::class], version = 1, exportSchema = false)
abstract class StreamDatabase : RoomDatabase() {
    abstract val streamDao: StreamDao
}
