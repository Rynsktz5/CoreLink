class Router(
    private val seenCache: SeenCache,
    private val myUserId: String
) {

    fun handleIncoming(message: Message) {

        println("📥 Incoming: ${message.content}")

        // STEP 1: Already seen?
        if (seenCache.isSeen(message.id)) {
            println("⚠️ Already seen, ignore")
            return
        }

        // STEP 2: Mark seen
        seenCache.markSeen(message.id)

        // STEP 3: Am I the receiver?
        if (message.receiverId == myUserId) {
            println("📩 I GOT THE MESSAGE: ${message.content}")
            return
        }

        // STEP 4: TTL check
        if (message.ttl <= 0) {
            println("❌ TTL finished, dropping message")
            return
        }

        // STEP 5: Reduce TTL
        message.ttl--

        // STEP 6: Forward (for now just print)
        println("🔁 Forwarding message, TTL=${message.ttl}")
    }
}