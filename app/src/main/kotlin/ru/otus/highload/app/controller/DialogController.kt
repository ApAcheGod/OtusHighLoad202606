package ru.otus.highload.app.controller

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.otus.highload.app.config.currentUserId
import ru.otus.highload.app.model.CounterResponse
import ru.otus.highload.app.service.ChatServiceClient
import ru.otus.highload.app.service.CounterServiceClient
import java.util.UUID

@RestController
class DialogController(
    private val chatServiceClient: ChatServiceClient,
    private val counterServiceClient: CounterServiceClient,
) {

    @PostMapping("/dialog/{userId}/send")
    fun send(
        @PathVariable userId: UUID,
        @RequestBody(required = false) body: String?,
        @RequestHeader("Content-Type", required = false) contentType: String?,
    ): ResponseEntity<ByteArray> =
        chatServiceClient.sendMessage(userId, body, contentType).asProxyResponse()

    @GetMapping("/dialog/{userId}/list")
    fun list(
        @PathVariable userId: UUID,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) limit: Int?,
    ): ResponseEntity<ByteArray> =
        chatServiceClient.listMessages(userId, offset, limit).asProxyResponse()

    @PutMapping("/dialog/{userId}/messages/{messageId}/read")
    fun markRead(@PathVariable userId: UUID, @PathVariable messageId: UUID): ResponseEntity<ByteArray> =
        chatServiceClient.markRead(userId, messageId).asProxyResponse()

    @GetMapping("/dialog/unread/count")
    fun unreadCount(): CounterResponse = counterServiceClient.unreadCount(currentUserId())

    private fun ResponseEntity<ByteArray>.asProxyResponse(): ResponseEntity<ByteArray> =
        ResponseEntity.status(statusCode)
            .contentType(headers.contentType ?: MediaType.APPLICATION_JSON)
            .body(body)
}
