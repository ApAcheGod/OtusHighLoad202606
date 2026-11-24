package ru.otus.highload.chat.service

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class ChatMetrics(
    private val registry: MeterRegistry,
) {
    fun <T> measure(operation: String, block: () -> T): T {
        val start = registry.config().clock().monotonicTime()
        var result = "success"
        try {
            return block()
        } catch (error: Throwable) {
            result = "error"
            throw error
        } finally {
            registry.counter(
                "chat_operations_total",
                "operation", operation,
                "result", result,
            ).increment()
            Timer.builder("chat_operation_duration_seconds")
                .tags("operation", operation)
                .publishPercentileHistogram()
                .register(registry)
                .record(registry.config().clock().monotonicTime() - start, TimeUnit.NANOSECONDS)
        }
    }
}
