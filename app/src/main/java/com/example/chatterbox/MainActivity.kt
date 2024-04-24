package com.example.chatterbox

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.chatterbox.adapters.MessageAdapter
import androidx.lifecycle.Observer
import com.example.chatterbox.database.AppDatabase
import com.example.chatterbox.database.Message
import com.example.chatterbox.viewmodel.ChatViewModel
import com.example.chatterbox.viewmodel.ViewModelFactory
import kotlinx.coroutines.*
import io.ably.lib.realtime.AblyRealtime

class MainActivity : AppCompatActivity() {
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var ablyRealtime: AblyRealtime
    private val scope = CoroutineScope(Dispatchers.Main)

    // Lazy initialization of the Room database
    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).fallbackToDestructiveMigration().build()
    }

    // Lazy initialization of the ViewModel
    private val viewModel by lazy {
        ViewModelProvider(this, ViewModelFactory(db.messageDao())).get(ChatViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize RecyclerView and its adapter
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        messageAdapter = MessageAdapter(mutableListOf())
        recyclerView.adapter = messageAdapter

        // Initialize inputs and buttons
        messageInput = findViewById(R.id.messageInput)  // Make sure this is before initializeChat()
        sendButton = findViewById(R.id.sendButton)

        // Ensure database and ViewModel are set up
        viewModel.allMessages.observe(this, Observer { messages ->
            messageAdapter.updateMessages(messages)
            recyclerView.scrollToPosition(messages.size - 1)
        })

        initializeChat()
    }

    private fun initializeChat() {
        ablyRealtime = AblyRealtime(getString(R.string.api_key))
        val pubChannel = ablyRealtime.channels.get(getString(R.string.pub_channel_name))
        val subChannel = ablyRealtime.channels.get(getString(R.string.sub_channel_name))

        subChannel.subscribe("default") { message ->
            val newMessage = Message(text = message.data.toString(), isSent = false)
            CoroutineScope(Dispatchers.IO).launch {
                viewModel.addMessage(newMessage)
            }
        }

        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString()
            if (messageText.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        pubChannel.publish("default", messageText)
                        val sentMessage = Message(text = messageText, isSent = true)
                        viewModel.addMessage(sentMessage)
                        withContext(Dispatchers.Main) {
                            messageInput.text.clear()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Failed to send message: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        Log.e("MainActivity", "Error sending message", e)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ablyRealtime.close()
        scope.cancel()
    }
}