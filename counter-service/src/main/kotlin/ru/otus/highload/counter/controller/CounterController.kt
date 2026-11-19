package ru.otus.highload.counter.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import ru.otus.highload.counter.model.CounterResponse
import ru.otus.highload.counter.service.CounterService
import java.util.UUID

@RestController
class CounterController(
    private val counterService: CounterService,
) {
    @GetMapping("/internal/counters/{userId}")
    fun unread(@PathVariable userId: UUID): CounterResponse =
        CounterResponse(userId, counterService.getUnread(userId))

    @PostMapping("/internal/counters/{userId}/reconcile")
    fun reconcile(@PathVariable userId: UUID): CounterResponse =
        CounterResponse(userId, counterService.reconcile(userId))
}
