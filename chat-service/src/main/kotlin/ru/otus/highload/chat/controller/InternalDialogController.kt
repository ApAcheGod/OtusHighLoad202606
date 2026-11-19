package ru.otus.highload.chat.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.otus.highload.chat.model.DialogSnapshot
import ru.otus.highload.chat.repository.DialogEventStorage
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController
class InternalDialogController(
    private val storage: DialogEventStorage,
    @Value("\${app.internal-token}") private val internalToken: String,
) {
    @GetMapping("/internal/dialogs/unread-snapshot/{userId}")
    fun unreadSnapshot(
        @PathVariable userId: UUID,
        @RequestHeader("X-Internal-Token", required = false) token: String?,
    ): DialogSnapshot {
        val matches = token != null && MessageDigest.isEqual(
            token.toByteArray(StandardCharsets.UTF_8),
            internalToken.toByteArray(StandardCharsets.UTF_8),
        )
        if (!matches) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token")
        return storage.unreadSnapshot(userId)
    }
}
