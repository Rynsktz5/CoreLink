package com.example.corelink

class Router(
    private val seenCache: SeenCache,
    private val myUserId: String
) {

    fun handleIncoming(message: Message): Pair<String, Message?> {

        // Dedup
        if (seenCache.isSeen(message.id)) {
            return "⚠️ Duplicate ignored\n" to null
        }

        seenCache.markSeen(message.id)

        // Deliver
        if (message.receiverId == myUserId) {
            return "📩 Received: ${message.content}\n" to null
        }

        // TTL
        if (message.ttl <= 0) {
            return "❌ TTL expired\n" to null
        }

        // Forward
        message.ttl--

        return "🔁 Forwarding (TTL=${message.ttl})\n" to message
    }
}