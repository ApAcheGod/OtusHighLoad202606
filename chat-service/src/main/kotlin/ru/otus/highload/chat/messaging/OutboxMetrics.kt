package ru.otus.highload.chat.messaging

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Component
class OutboxMetrics(
    registry: MeterRegistry,
) {
    private val backlog = AtomicInteger()
    private val publishErrors = Counter.builder("chat_outbox_publish_errors_total").register(registry)

    init {
        Gauge.builder("chat_outbox_backlog", backlog) { it.get().toDouble() }.register(registry)
    }

    fun observeBacklog(size: Int) {
        backlog.set(size)
    }

    fun publishError() {
        publishErrors.increment()
    }
}
