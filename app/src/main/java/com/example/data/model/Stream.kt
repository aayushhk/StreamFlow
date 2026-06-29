package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@Entity(tableName = "streams")
@JsonClass(generateAdapter = true)
data class Stream(
    @PrimaryKey
    val url: String,
    val channel: String?,
    val feed: String?,
    val title: String,
    val quality: String?,
    val label: String?,
    @Json(name = "user_agent") val userAgent: String?,
    val referrer: String?
)
