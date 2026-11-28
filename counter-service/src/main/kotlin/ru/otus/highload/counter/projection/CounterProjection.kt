package ru.otus.highload.counter.projection

import ru.otus.highload.counter.model.CounterSnapshot
import ru.otus.highload.utils.events.CounterEvent
import java.util.UUID

interface CounterProjection {
    fun applySent(event: CounterEvent): ApplyResult
    fun applyRead(event: CounterEvent): ApplyResult
    fun getUnread(userId: UUID): Long?
    fun restore(snapshot: CounterSnapshot): Long
}
