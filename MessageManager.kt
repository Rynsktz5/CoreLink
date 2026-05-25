import java.util.UUID

class MessageManager(
    private val router: Router,
    private val myUserId: String
) {

    fun send(receiverId: String, text: String) {

        val message = Message(
            id = UUID.randomUUID().toString(),
            senderId = myUserId,
            receiverId = receiverId,
            content = text,
            timestamp = System.currentTimeMillis(),
            ttl = 5
        )

        println("📤 Sending: $text")

        // simulate sending
        router.handleIncoming(message)
    }
}