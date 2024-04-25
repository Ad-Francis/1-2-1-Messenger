package com.example.chatterbox.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    var isSent: Boolean,
    var status: String
)