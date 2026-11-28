package ru.otus.highload.utils.events

enum class CounterEventType {
    MESSAGE_SENT,
    MESSAGE_READ,
    COMPENSATION_APPLIED,
    MARK_MESSAGE_NOT_UNREAD,
    RESTORE_UNREAD_MESSAGE,
}

data class CounterEvent(
    val eventId: String,
    val sagaId: String,
    val messageId: String,
    val userId: String,
    val version: Long,
    val type: CounterEventType,
    val occurredAtNs: Long,
    val error: String? = null,
)
