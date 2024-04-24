package com.example.chatterbox.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.chatterbox.database.Message
import com.example.chatterbox.R

class MessageAdapter(private var messages: MutableList<Message>) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_MESSAGE_SENT) R.layout.item_message_sent else R.layout.item_message_received
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return MessageViewHolder(view, viewType)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isSent) VIEW_TYPE_MESSAGE_SENT else VIEW_TYPE_MESSAGE_RECEIVED
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = when (viewType) {
            VIEW_TYPE_MESSAGE_SENT -> itemView.findViewById(R.id.tvMessageSent)
            VIEW_TYPE_MESSAGE_RECEIVED -> itemView.findViewById(R.id.tvMessageReceived)
            else -> throw IllegalStateException("Unknown view type: $viewType")
        }

        fun bind(message: Message) {
            textView.text = message.text
        }
    }

    companion object {
        private const val VIEW_TYPE_MESSAGE_SENT = 1
        private const val VIEW_TYPE_MESSAGE_RECEIVED = 2
    }
}