package ru.otus.highload.chat.messaging

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.otus.highload.chat.repository.DialogEventStorage
import ru.otus.highload.utils.events.CounterTopics
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
class DialogOutboxRelay(
    private val storage: DialogEventStorage,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Qualifier("counterEventObjectMapper") private val mapper: ObjectMapper,
    private val metrics: OutboxMetrics,
    @Value("\${app.kafka.send-timeout-ms:10000}") private val sendTimeoutMs: Long = 10_000,
) {
    @Scheduled(fixedDelayString = "\${app.counter.outbox-poll-ms:100}")
    fun publishBatch() {
        try {
            val batch = storage.outboxBatch(100)
            metrics.observeBacklog(batch.size)
            batch.forEach { event ->
                kafkaTemplate.send(CounterTopics.EVENTS, event.messageId, mapper.writeValueAsString(event))
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS)
                storage.markPublished(UUID.fromString(event.eventId))
            }
        } catch (ex: Exception) {
            metrics.publishError()
            throw ex
        }
    }
}
