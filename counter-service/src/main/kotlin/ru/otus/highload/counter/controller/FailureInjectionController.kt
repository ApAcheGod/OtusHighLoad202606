package ru.otus.highload.counter.controller

import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.otus.highload.counter.config.FailureInjectionConfig
import ru.otus.highload.utils.events.CounterEventType

@Profile("hw10-fault")
@RestController
class FailureInjectionController(
    private val failures: FailureInjectionConfig,
) {
    @PutMapping("/internal/failure-injection/{eventType}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun fail(@PathVariable eventType: CounterEventType) {
        failures.add(eventType)
    }
}
