package com.example.chatterbox.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatterbox.database.Message
import com.example.chatterbox.database.MessageDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatViewModel(private val messageDao: MessageDao) : ViewModel() {

    // LiveData to observe messages
    val allMessages: LiveData<List<Message>> = messageDao.getAllMessages()

    fun addMessage(message: Message) {
        viewModelScope.launch(Dispatchers.IO) { // Launch coroutine in IO dispatcher
            try {
                messageDao.insertMessage(message)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error adding message", e)
            }
        }
    }

    fun updateMessage(message: Message) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                messageDao.updateMessage(message)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error updating message", e)
            }
        }
    }
}