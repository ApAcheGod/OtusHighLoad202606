package ru.otus.highload.app.model

import java.util.UUID

data class PostUpdateRequest(
    val id: UUID,
    val text: String
)