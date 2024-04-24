package com.example.chatterbox.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {
    @Query("SELECT * FROM message")
    fun getAllMessages(): LiveData<List<Message>>

    @Insert
    fun insertMessage(message: Message)
}