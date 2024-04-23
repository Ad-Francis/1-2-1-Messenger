package com.example.chatterbox

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button
import android.widget.EditText
import com.example.chatterbox.adapters.MessageAdapter
import com.example.chatterbox.models.Message
import kotlinx.coroutines.*
import io.ably.lib.realtime.AblyRealtime

class MainActivity : AppCompatActivity() {
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var ablyRealtime: AblyRealtime
    private val scope = CoroutineScope(Dispatchers.Main)
    private val messages = mutableListOf<Message>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        recyclerView = findViewById(R.id.recyclerView)

        recyclerView.layoutManager = LinearLayoutManager(this)
        messageAdapter = MessageAdapter(messages)
        recyclerView.adapter = messageAdapter

        initializeChat()
    }

    private fun initializeChat() {
        // Assuming you have already initialized AblyRealtime instance elsewhere in your setup
        val apiKey = getString(R.string.api_key) // Make sure this is defined in your strings.xml
        ablyRealtime = AblyRealtime(apiKey)

        val pubChannelName = getString(R.string.pub_channel_name) // These should be in your strings.xml
        val subChannelName = getString(R.string.sub_channel_name)

        val pubChannel = ablyRealtime.channels.get(pubChannelName)
        val subChannel = ablyRealtime.channels.get(subChannelName)

        // Subscribe to receive messages
        subChannel.subscribe("default") { message ->
            val newMessage = Message(message.data.toString(), false) // false indicates a received message
            runOnUiThread {
                messages.add(newMessage) // Add message to the list
                messageAdapter.notifyItemInserted(messages.size - 1) // Notify the adapter to refresh the view
                recyclerView.scrollToPosition(messages.size - 1) // Scroll to the bottom to show new message
            }
        }

        // Send messages when the button is clicked
        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString()
            if (messageText.isNotBlank()) {
                scope.launch {
                    pubChannel.publish("default", messageText) // Publish message
                    runOnUiThread {
                        val sentMessage = Message(messageText, true) // true indicates a sent message
                        messages.add(sentMessage)
                        messageAdapter.notifyItemInserted(messages.size - 1)
                        recyclerView.scrollToPosition(messages.size - 1)
                        messageInput.text.clear() // Clear the input field after sending
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