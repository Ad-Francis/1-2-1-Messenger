package com.example.chatterbox.models

data class Message(
    val text: String,
    val isSent: Boolean  // true if this is a sent message, false if received
)