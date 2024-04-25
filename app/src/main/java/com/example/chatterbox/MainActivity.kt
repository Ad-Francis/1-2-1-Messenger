package com.example.chatterbox

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.ConnectionStateListener
import io.ably.lib.types.ErrorInfo
import io.ably.lib.realtime.CompletionListener
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.example.chatterbox.adapters.MessageAdapter
import com.example.chatterbox.database.AppDatabase
import com.example.chatterbox.database.Message
import com.example.chatterbox.viewmodel.ChatViewModel
import com.example.chatterbox.viewmodel.ViewModelFactory
import io.ably.lib.realtime.ConnectionState
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var ablyRealtime: AblyRealtime
    private val scope = CoroutineScope(Dispatchers.Main)

    private val CHANNEL_ID = "chat_messages_channel"

    private val db by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "database-name").fallbackToDestructiveMigration().build()
    }

    private val viewModel by lazy {
        ViewModelProvider(this, ViewModelFactory(db.messageDao())).get(ChatViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createNotificationChannel()
        setupViews()
        setupObservers()
        initializeChat()

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        messageAdapter = MessageAdapter(mutableListOf())
        recyclerView.adapter = messageAdapter
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        recyclerView.requestFocus()
    }

    private fun setupObservers() {
        viewModel.allMessages.observe(this, Observer { messages ->
            messageAdapter.updateMessages(messages)
            recyclerView.scrollToPosition(messages.size - 1)
        })
    }

    private fun reconnectAbly() {
        // Check if still disconnected before trying to reconnect
        if (ablyRealtime.connection.state == ConnectionState.disconnected ||
            ablyRealtime.connection.state == ConnectionState.suspended) {
            ablyRealtime.connect() // This method might vary based on SDK specifics
        }
    }

    private fun initializeChat() {
        ablyRealtime = AblyRealtime(getString(R.string.api_key))

        ablyRealtime.connection.on(ConnectionStateListener { stateChange ->
            Log.d("AblyConnection", "Connection state changed: ${stateChange.current.name}")
            when(stateChange.current) {
                ConnectionState.connected -> {
                    // Connection established
                }
                ConnectionState.disconnected, ConnectionState.suspended -> {
                    Toast.makeText(this, "Connection lost, attempting to reconnect...", Toast.LENGTH_SHORT).show()
                    // Attempt to reconnect
                    reconnectAbly()
                }
                ConnectionState.failed -> {
                    Toast.makeText(this, "Connection failed, check network or API key", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        })

        val pubChannel = ablyRealtime.channels.get(getString(R.string.pub_channel_name))
        val subChannel = ablyRealtime.channels.get(getString(R.string.sub_channel_name))

        subChannel.subscribe("default") { message ->
            val newMessage = Message(text = message.data.toString(), isSent = false, status = "received")
            CoroutineScope(Dispatchers.IO).launch {
                viewModel.addMessage(newMessage)
                sendNotification(newMessage)
            }
        }

        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString()
            if (messageText.isNotBlank()) {
                val newMessage = Message(text = messageText, isSent = false, status = "pending")
                viewModel.addMessage(newMessage)
                messageInput.text.clear()
                Toast.makeText(applicationContext, "Message pending...", Toast.LENGTH_SHORT).show()

                pubChannel.publish("default", messageText, object : CompletionListener {
                    override fun onSuccess() {
                        runOnUiThread {
                            newMessage.isSent = true
                            newMessage.status = "sent"
                            viewModel.updateMessage(newMessage)
                            Toast.makeText(applicationContext, "Message sent successfully", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onError(errorInfo: ErrorInfo?) {
                        runOnUiThread {
                            newMessage.status = "failed"
                            viewModel.updateMessage(newMessage)
                            Toast.makeText(applicationContext, "Failed to send message: ${errorInfo?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendNotification(message: Message) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_message)
            .setContentTitle("New Message")
            .setContentText(message.text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(this)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ablyRealtime.close()
        scope.cancel()
    }
}
