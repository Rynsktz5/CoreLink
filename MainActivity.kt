override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val seenCache = SeenCache()
    val router = Router(seenCache, "userB")
    val manager = MessageManager(router, "userA")

    // TEST 1
    manager.send("userB", "meow meow niqqa")

    // TEST 2
    manager.send("userC", "Not for me")

    // TEST 3 (duplicate)
    val msg = Message(
        id = "fixed-id",
        senderId = "userA",
        receiverId = "userB",
        content = "Duplicate test",
        timestamp = System.currentTimeMillis(),
        ttl = 5
    )

    router.handleIncoming(msg)
    router.handleIncoming(msg)
}