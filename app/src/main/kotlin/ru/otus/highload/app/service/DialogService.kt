package ru.otus.highload.app.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.otus.highload.app.config.currentUserId
import ru.otus.highload.app.model.DialogMessage
import ru.otus.highload.app.repository.DialogRepository
import ru.otus.highload.utils.uuidV7
import java.time.Instant
import java.util.UUID

@Service
class DialogService(
    private val dialogRepository: DialogRepository,
) {

    @Transactional
    fun sendMessage(toUserId: UUID, text: String, clientMessageId: UUID? = null): UUID {
        val fromUserId = currentUserId()
        require(toUserId != fromUserId) { "Cannot send a message to yourself" }
        require(text.isNotBlank()) { "text must not be blank" }
        require(text.length <= MAX_TEXT_LENGTH) { "text must not exceed $MAX_TEXT_LENGTH characters" }

        val (userLowId, userHighId) = orderedPair(fromUserId, toUserId)
        val messageId = uuidV7()
        val message = DialogMessage(
            id = messageId,
            userLowId = userLowId,
            userHighId = userHighId,
            fromUserId = fromUserId,
            text = text,
            createdAt = Instant.now(),
        )

        val inserted = dialogRepository.save(message, clientMessageId)
        if (!inserted && clientMessageId != null) {
            return dialogRepository.findIdByClientMessageId(
                pairKey = DialogRepository.pairKey(userLowId, userHighId),
                clientMessageId = clientMessageId,
            ) ?: messageId
        }
        return messageId
    }

    fun listMessages(withUserId: UUID, offset: Int? = null, limit: Int? = null): List<DialogMessage> {
        val userId = currentUserId()
        require(withUserId != userId) { "Cannot list a dialog with yourself" }
        require(offset == null || offset >= 0) { "offset must be >= 0" }
        require(limit == null || limit in 1..MAX_PAGE_SIZE) { "limit must be between 1 and $MAX_PAGE_SIZE" }

        val (userLowId, userHighId) = orderedPair(userId, withUserId)
        return dialogRepository.findConversation(
            userLowId = userLowId,
            userHighId = userHighId,
            offset = offset ?: 0,
            limit = limit,
        )
    }

    companion object {
        const val MAX_PAGE_SIZE = 100
        const val MAX_TEXT_LENGTH = 4096

        fun orderedPair(a: UUID, b: UUID): Pair<UUID, UUID> =
            if (a.toString() <= b.toString()) a to b else b to a
    }
}
