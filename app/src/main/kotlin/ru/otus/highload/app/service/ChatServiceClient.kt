package ru.otus.highload.app.service

import org.slf4j.MDC
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import ru.otus.highload.app.config.RequestIdFilter
import java.util.UUID

@Component
class ChatServiceClient(
    private val restClient: RestClient,
) {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    fun sendMessage(userId: UUID, body: String?, contentType: String?): ResponseEntity<ByteArray> =
        restClient.post()
            .uri("/api/v1/dialogs/{userId}/send", userId)
            .apply { propagateHeaders() }
            .apply { if (body != null) contentType(parseMediaType(contentType)).body(body) }
            .retrieve()
            .toEntity(ByteArray::class.java)
            .also { log.info("proxy dialog send -> chat-service: status={} bodyLen={}", it.statusCode, it.body?.size) }

    fun listMessages(userId: UUID, offset: Int?, limit: Int?): ResponseEntity<ByteArray> {
        val query = buildString {
            offset?.let { append("offset=").append(it).append('&') }
            limit?.let { append("limit=").append(it) }
        }.removeSuffix("&")
        val path = "/api/v1/dialogs/$userId/list" + if (query.isNotEmpty()) "?$query" else ""
        return restClient.get()
            .uri(path)
            .apply { propagateHeaders() }
            .retrieve()
            .toEntity(ByteArray::class.java)
            .also { log.info("proxy dialog list -> chat-service: status={} bodyLen={}", it.statusCode, it.body?.size) }
    }

    private fun RestClient.RequestHeadersSpec<*>.propagateHeaders() {
        currentRequest()?.getHeader("Authorization")?.let { header("Authorization", it) }
        MDC.get(RequestIdFilter.MDC_KEY)?.let { header(RequestIdFilter.HEADER, it) }
    }

    private fun currentRequest() =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

    private fun parseMediaType(contentType: String?): MediaType =
        contentType?.let { MediaType.parseMediaType(it) } ?: MediaType.APPLICATION_JSON
}
