package ru.otus.highload.chat.repository

import ru.otus.highload.chat.model.DialogMessage
import java.util.UUID

interface DialogStorage {
    fun send(fromUserId: UUID, toUserId: UUID, text: String, clientMessageId: UUID?): UUID
    fun list(userId: UUID, withUserId: UUID, offset: Int, limit: Int?): List<DialogMessage>
}
