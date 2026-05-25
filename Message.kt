data class Message(
    val id: String,          // unique id (important)
    val senderId: String,   // who sent
    val receiverId: String, // who should receive
    val content: String,    // actual message
    val timestamp: Long,    // time
    var ttl: Int            // how many hops left
)