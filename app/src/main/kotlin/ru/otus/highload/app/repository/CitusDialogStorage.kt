package ru.otus.highload.app.repository

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.otus.highload.app.model.DialogMessage
import ru.otus.highload.app.service.DialogService
import ru.otus.highload.utils.uuidV7
import java.time.Instant
import java.util.UUID

@Component
@ConditionalOnProperty(name = ["app.dialog.storage"], havingValue = "citus", matchIfMissing = true)
class CitusDialogStorage(
    private val dialogRepository: DialogRepository,
) : DialogStorage {
    @Transactional
    override fun send(fromUserId: UUID, toUserId: UUID, text: String, clientMessageId: UUID?): UUID {
        require(toUserId != fromUserId) { "Cannot send a message to yourself" }
        require(text.isNotBlank()) { "text must not be blank" }
        require(text.length <= DialogService.MAX_TEXT_LENGTH) { "text must not exceed 4096 characters" }
        val (low, high) = DialogService.orderedPair(fromUserId, toUserId)
        val dialogId = DialogRepository.pairKey(low, high)
        val messageId = uuidV7()
        val message = DialogMessage(messageId, dialogId, fromUserId, toUserId, text, Instant.now())
        val inserted = dialogRepository.save(message, clientMessageId)
        if (!inserted && clientMessageId != null) {
            return dialogRepository.findIdByClientMessageId(dialogId, clientMessageId) ?: messageId
        }
        return messageId
    }

    @Transactional(readOnly = true)
    override fun list(userId: UUID, withUserId: UUID, offset: Int, limit: Int?): List<DialogMessage> {
        require(withUserId != userId) { "Cannot list a dialog with yourself" }
        require(offset >= 0) { "offset must be >= 0" }
        require(limit == null || limit in 1..DialogService.MAX_PAGE_SIZE) { "limit must be between 1 and 100" }
        val (low, high) = DialogService.orderedPair(userId, withUserId)
        return dialogRepository.findConversation(low, high, offset, limit)
    }
}
