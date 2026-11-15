package ru.otus.highload.app.controller

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.otus.highload.app.service.ChatServiceClient
import java.util.UUID

@RestController
class DialogController(
    private val chatServiceClient: ChatServiceClient,
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

    private fun ResponseEntity<ByteArray>.asProxyResponse(): ResponseEntity<ByteArray> =
        ResponseEntity.status(statusCode)
            .contentType(headers.contentType ?: MediaType.APPLICATION_JSON)
            .body(body)
}
