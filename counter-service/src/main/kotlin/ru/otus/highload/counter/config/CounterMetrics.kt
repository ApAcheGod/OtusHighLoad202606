package ru.otus.highload.counter.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import ru.otus.highload.utils.events.CounterEventType

@Component
class CounterMetrics(
    private val registry: MeterRegistry,
) {
    fun saga(type: CounterEventType, result: String) {
        Counter.builder("counter_saga_total")
            .tags("type", type.name, "result", result)
            .register(registry)
            .increment()
    }

    fun invariantViolation() {
        Counter.builder("counter_projection_invariant_total").register(registry).increment()
    }

    fun restored() {
        Counter.builder("counter_restore_total").register(registry).increment()
    }
}
