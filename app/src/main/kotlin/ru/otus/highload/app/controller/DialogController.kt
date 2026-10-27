package ru.otus.highload.app.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.otus.highload.app.model.DialogMessage
import ru.otus.highload.app.model.DialogSendRequest
import ru.otus.highload.app.model.MessageResponse
import ru.otus.highload.app.service.DialogService
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@RestController
class DialogController(
    private val dialogService: DialogService,
) {

    @PostMapping("/dialog/{userId}/send")
    fun send(
        @PathVariable userId: UUID,
        @RequestBody request: DialogSendRequest,
    ): ResponseEntity<UUID> {
        val messageId = dialogService.sendMessage(userId, request.text, request.clientMessageId)
        return ResponseEntity.ok(messageId)
    }

    @GetMapping("/dialog/{userId}/list")
    fun list(
        @PathVariable userId: UUID,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) limit: Int?,
    ): ResponseEntity<List<MessageResponse>> {
        val messages = dialogService.listMessages(userId, offset, limit)
        return ResponseEntity.ok(messages.map { it.toResponse() })
    }

    private fun DialogMessage.toResponse() = MessageResponse(
        id = id,
        fromUserId = fromUserId,
        toUserId = if (fromUserId == userLowId) userHighId else userLowId,
        text = text,
        createdAt = DateTimeFormatter.ISO_INSTANT.format(createdAt.atOffset(ZoneOffset.UTC)),
    )
}
