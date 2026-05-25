package com.example.corelink

import android.view.*
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: MutableList<Pair<String, Boolean>>) :
    RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.messageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (msg, isSent) = messages[position]

        holder.text.text = msg

        val params = holder.text.layoutParams as ViewGroup.MarginLayoutParams

        if (isSent) {
            holder.text.setBackgroundResource(R.drawable.bubble_sent)
            holder.text.setTextColor(ContextCompat.getColor(holder.text.context, android.R.color.white))
            params.marginStart = 120
            params.marginEnd = 0
        } else {
            holder.text.setBackgroundResource(R.drawable.bubble_received)
            holder.text.setTextColor(ContextCompat.getColor(holder.text.context, R.color.textPrimary))
            params.marginStart = 0
            params.marginEnd = 120
        }

        holder.text.layoutParams = params
    }
}
