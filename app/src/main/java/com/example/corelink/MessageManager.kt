package com.example.corelink

import java.util.UUID

class MessageManager(
    private val router: Router,
    private val bt: BluetoothService,
    private val myUserId: String
) {

    fun send(receiverId: String, content: String): String {

        val message = Message(
            id = UUID.randomUUID().toString(),
            senderId = myUserId,
            receiverId = receiverId,
            content = content,
            timestamp = System.currentTimeMillis(),
            ttl = 5
        )

        val (log, forwardMsg) = router.handleIncoming(message)

        // send via bluetooth
        if (forwardMsg != null) {
            bt.sendMessage(forwardMsg.content)
        }

        return log
    }
}