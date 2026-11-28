package ru.otus.highload.counter.config

import org.springframework.stereotype.Component
import ru.otus.highload.counter.messaging.PermanentCounterException
import ru.otus.highload.utils.events.CounterEventType
import java.util.concurrent.ConcurrentHashMap

@Component
class FailureInjectionConfig {
    private val failures = ConcurrentHashMap.newKeySet<CounterEventType>()

    fun add(type: CounterEventType) {
        failures.add(type)
    }

    fun remove(type: CounterEventType) {
        failures.remove(type)
    }

    fun failIfRequested(type: CounterEventType) {
        if (failures.contains(type)) {
            throw PermanentCounterException("Injected permanent failure for $type")
        }
    }
}
