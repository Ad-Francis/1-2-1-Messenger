package com.example.chatterbox

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import io.ably.lib.realtime.AblyRealtime
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
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        messageAdapter = MessageAdapter(mutableListOf())
        recyclerView.adapter = messageAdapter
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
    }

    private fun setupObservers() {
        viewModel.allMessages.observe(this, Observer { messages ->
            messageAdapter.updateMessages(messages)
            recyclerView.scrollToPosition(messages.size - 1)
        })
    }

    private fun initializeChat() {
        ablyRealtime = AblyRealtime(getString(R.string.api_key))
        val pubChannel = ablyRealtime.channels.get(getString(R.string.pub_channel_name))
        val subChannel = ablyRealtime.channels.get(getString(R.string.sub_channel_name))

        subChannel.subscribe("default") { message ->
            val newMessage = Message(text = message.data.toString(), isSent = false)
            CoroutineScope(Dispatchers.IO).launch {
                viewModel.addMessage(newMessage)
                sendNotification(newMessage)
            }
        }

        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString()
            if (messageText.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    pubChannel.publish("default", messageText)
                    val sentMessage = Message(text = messageText, isSent = true)
                    viewModel.addMessage(sentMessage)
                    withContext(Dispatchers.Main) {
                        messageInput.text.clear()
                    }
                }
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
            .setSmallIcon(R.drawable.ic_message)
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