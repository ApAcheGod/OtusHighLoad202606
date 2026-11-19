package ru.otus.highload.chat.repository

import ru.otus.highload.chat.model.DialogSnapshot
import ru.otus.highload.utils.events.CounterEvent
import java.util.UUID

interface DialogEventStorage {
    fun outboxBatch(limit: Int): List<CounterEvent>
    fun markPublished(eventId: UUID)
    fun applyCompensation(event: CounterEvent): String
    fun unreadSnapshot(userId: UUID): DialogSnapshot
}
