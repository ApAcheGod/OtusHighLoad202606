package ru.otus.highload.app.model

import java.time.Instant
import java.util.UUID

data class DialogMessage(
    val id: UUID,
    val userLowId: UUID,
    val userHighId: UUID,
    val fromUserId: UUID,
    val text: String,
    val createdAt: Instant,
)
