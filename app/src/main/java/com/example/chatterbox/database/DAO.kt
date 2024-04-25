package com.example.chatterbox.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MessageDao {
    @Query("SELECT * FROM message")
    fun getAllMessages(): LiveData<List<Message>>

    @Insert
    fun insertMessage(message: Message)

    @Update
    fun updateMessage(message: Message)
}