package ru.otus.highload.chat.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.otus.highload.chat.model.DialogMessage
import ru.otus.highload.chat.model.DialogSendRequest
import ru.otus.highload.chat.model.MessageResponse
import ru.otus.highload.chat.service.DialogService
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@RestController
class DialogController(
    private val dialogService: DialogService,
) {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    @PostMapping("/api/v1/dialogs/{userId}/send")
    fun send(
        @PathVariable userId: UUID,
        @RequestBody request: DialogSendRequest,
    ): ResponseEntity<UUID> {
        val messageId = dialogService.sendMessage(userId, request.text, request.clientMessageId)
        log.info("dialog send: toUserId={} textLen={} messageId={}", userId, request.text.length, messageId)
        return ResponseEntity.ok(messageId)
    }

    @GetMapping("/api/v1/dialogs/{userId}/list")
    fun list(
        @PathVariable userId: UUID,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) limit: Int?,
    ): ResponseEntity<List<MessageResponse>> {
        val messages = dialogService.listMessages(userId, offset, limit)
        log.info("dialog list: withUserId={} count={}", userId, messages.size)
        return ResponseEntity.ok(messages.map { it.toResponse() })
    }

    private fun DialogMessage.toResponse() = MessageResponse(
        id = id,
        fromUserId = fromUserId,
        toUserId = toUserId,
        text = text,
        createdAt = DateTimeFormatter.ISO_INSTANT.format(createdAt.atOffset(ZoneOffset.UTC)),
    )
}
