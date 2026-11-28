package ru.otus.highload.counter.messaging

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.stereotype.Component
import ru.otus.highload.counter.config.CounterMetrics
import ru.otus.highload.counter.config.FailureInjectionConfig
import ru.otus.highload.utils.events.CounterEvent
import ru.otus.highload.utils.events.CounterEventType
import ru.otus.highload.utils.events.CounterTopics
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@Component
class CounterEventRecoverer(
    private val compensator: SagaCompensator,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Qualifier("counterEventObjectMapper") private val mapper: ObjectMapper,
    private val metrics: CounterMetrics,
    private val failures: FailureInjectionConfig,
    @Value("\${app.kafka.send-timeout-ms:10000}") private val sendTimeoutMs: Long = 10_000,
) : ConsumerRecordRecoverer {
    override fun accept(record: ConsumerRecord<*, *>, exception: Exception) {
        val cause = exception.cause ?: exception
        val event = runCatching {
            mapper.readValue(record.value().toString(), CounterEvent::class.java)
        }.getOrNull()
        if (event != null && event.type != CounterEventType.COMPENSATION_APPLIED) {
            compensator.compensate(event, cause.message ?: cause::class.java.name)
            metrics.saga(event.type, "COMPENSATING")
        }

        val deadLetter = ProducerRecord<String, String>(
            CounterTopics.DLT,
            record.key()?.toString(),
            record.value()?.toString(),
        )
        deadLetter.headers().add(
            "counter_error",
            (cause.message ?: cause::class.java.name).take(1_000).toByteArray(StandardCharsets.UTF_8),
        )
        kafkaTemplate.send(deadLetter).get(sendTimeoutMs, TimeUnit.MILLISECONDS)
        event?.let { failures.remove(it.type) }
    }
}
