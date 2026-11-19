package ru.otus.highload.counter.messaging

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import ru.otus.highload.counter.config.CounterMetrics
import ru.otus.highload.counter.config.FailureInjectionConfig
import ru.otus.highload.counter.projection.ApplyResult
import ru.otus.highload.counter.projection.CounterProjection
import ru.otus.highload.utils.events.CounterEvent
import ru.otus.highload.utils.events.CounterEventType
import ru.otus.highload.utils.events.CounterTopics
import tools.jackson.databind.ObjectMapper

@Component
class CounterEventConsumer(
    private val projection: CounterProjection,
    private val compensator: SagaCompensator,
    @Qualifier("counterEventObjectMapper") private val mapper: ObjectMapper,
    private val metrics: CounterMetrics,
    private val failures: FailureInjectionConfig,
) {
    @KafkaListener(
        topics = [CounterTopics.EVENTS],
        groupId = "counter-service",
        containerFactory = "counterListenerContainerFactory",
    )
    fun onEvent(message: String, acknowledgment: Acknowledgment) {
        val event = mapper.readValue(message, CounterEvent::class.java)
        failures.failIfRequested(event.type)
        val result = when (event.type) {
            CounterEventType.MESSAGE_SENT -> projection.applySent(event)
            CounterEventType.MESSAGE_READ -> projection.applyRead(event)
            CounterEventType.COMPENSATION_APPLIED -> {
                compensator.markCompensated(event)
                ApplyResult.APPLIED
            }
            else -> throw PermanentCounterException("Unsupported event type ${event.type}")
        }
        if (result == ApplyResult.INVARIANT_VIOLATION) {
            metrics.invariantViolation()
            throw PermanentCounterException("Unread counter would become negative for ${event.messageId}")
        }
        metrics.saga(
            event.type,
            if (event.type == CounterEventType.COMPENSATION_APPLIED) "COMPENSATED" else result.name,
        )
        acknowledgment.acknowledge()
    }
}
