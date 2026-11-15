package ru.otus.highload.chat.service

import org.springframework.stereotype.Service
import ru.otus.highload.chat.config.currentUserId
import ru.otus.highload.chat.model.DialogMessage
import ru.otus.highload.chat.repository.DialogStorage
import java.util.UUID

@Service
class DialogService(
    private val dialogStorage: DialogStorage,
) {
    fun sendMessage(toUserId: UUID, text: String, clientMessageId: UUID? = null): UUID =
        dialogStorage.send(currentUserId(), toUserId, text, clientMessageId)

    fun listMessages(withUserId: UUID, offset: Int?, limit: Int?): List<DialogMessage> =
        dialogStorage.list(currentUserId(), withUserId, offset ?: 0, limit)

    companion object {
        const val MAX_PAGE_SIZE = 100
        const val MAX_TEXT_LENGTH = 4096
        fun orderedPair(a: UUID, b: UUID): Pair<UUID, UUID> =
            if (a.toString() <= b.toString()) a to b else b to a
    }
}
