package ru.otus.highload.app.service

import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import ru.otus.highload.app.config.RequestIdFilter
import ru.otus.highload.app.model.CounterResponse
import java.util.UUID

@Component
class CounterServiceClient(
    @Qualifier("counterServiceRestClient") private val restClient: RestClient,
    @Value("\${app.internal-token}") private val internalToken: String,
) {
    fun unreadCount(userId: UUID): CounterResponse = restClient.get()
        .uri("/internal/counters/{userId}", userId)
        .header("X-Internal-Token", internalToken)
        .apply { MDC.get(RequestIdFilter.MDC_KEY)?.let { header(RequestIdFilter.HEADER, it) } }
        .retrieve()
        .body(CounterResponse::class.java)
        ?: error("Counter service returned empty response")
}
