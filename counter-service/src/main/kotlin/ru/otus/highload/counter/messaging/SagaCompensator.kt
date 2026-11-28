package ru.otus.highload.counter.messaging

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import ru.otus.highload.utils.events.CounterEvent
import ru.otus.highload.utils.events.CounterEventType
import ru.otus.highload.utils.events.CounterTopics
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
class SagaCompensator(
    private val redis: StringRedisTemplate,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Qualifier("counterEventObjectMapper") private val mapper: ObjectMapper,
    @Value("\${app.kafka.send-timeout-ms:10000}") private val sendTimeoutMs: Long = 10_000,
) {
    private val stateScript = DefaultRedisScript<String>().apply {
        setLocation(ClassPathResource("valkey/saga_state.lua"))
        setResultType(String::class.java)
    }

    fun compensate(event: CounterEvent, cause: String) {
        val state = transition(event, "COMPENSATING", cause)
        if (state != "COMPENSATING") return
        val type = when (event.type) {
            CounterEventType.MESSAGE_SENT -> CounterEventType.MARK_MESSAGE_NOT_UNREAD
            CounterEventType.MESSAGE_READ -> CounterEventType.RESTORE_UNREAD_MESSAGE
            else -> throw PermanentCounterException("Cannot compensate ${event.type}")
        }
        val compensation = event.copy(
            eventId = UUID.randomUUID().toString(),
            type = type,
            occurredAtNs = System.nanoTime(),
            error = cause.take(1_000),
        )
        kafkaTemplate.send(CounterTopics.COMPENSATIONS, event.messageId, mapper.writeValueAsString(compensation))
            .get(sendTimeoutMs, TimeUnit.MILLISECONDS)
    }

    fun markCompensated(event: CounterEvent) {
        transition(event, "COMPENSATED", event.error.orEmpty())
    }

    private fun transition(event: CounterEvent, state: String, error: String): String = redis.execute(
        stateScript,
        listOf("unread:{${event.userId}}:saga:${event.sagaId}"),
        state,
        event.eventId,
        event.version.toString(),
        event.messageId,
        event.type.name,
        error.take(1_000),
        System.currentTimeMillis().toString(),
    ) ?: error("Valkey returned no SAGA result")
}
