package ru.otus.highload.app.model

import java.time.Instant
import java.util.UUID

data class DialogMessage(
    val id: UUID,
    val dialogId: String,
    val fromUserId: UUID,
    val toUserId: UUID,
    val text: String,
    val createdAt: Instant,
)
