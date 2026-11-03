package ru.otus.highload.app.repository

import ru.otus.highload.app.model.DialogMessage
import java.util.UUID

interface DialogStorage {
    fun send(fromUserId: UUID, toUserId: UUID, text: String, clientMessageId: UUID?): UUID
    fun list(userId: UUID, withUserId: UUID, offset: Int, limit: Int?): List<DialogMessage>
}
