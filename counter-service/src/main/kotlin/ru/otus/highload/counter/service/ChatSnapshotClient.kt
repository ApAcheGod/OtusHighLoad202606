package ru.otus.highload.counter.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import ru.otus.highload.counter.model.CounterSnapshot
import java.util.UUID

@Component
class ChatSnapshotClient(
    builder: RestClient.Builder,
    @Value("\${app.chat-service.url:http://localhost:8083}") chatServiceUrl: String,
    @Value("\${app.internal-token}") private val internalToken: String,
) {
    private val client = builder.baseUrl(chatServiceUrl).build()

    fun snapshot(userId: UUID): CounterSnapshot = client.get()
        .uri("/internal/dialogs/unread-snapshot/{userId}", userId)
        .header("X-Internal-Token", internalToken)
        .retrieve()
        .body(CounterSnapshot::class.java)
        ?: error("Chat service returned empty snapshot")
}
