package ru.otus.highload.post.model

import java.util.UUID

data class Post(
    val id: UUID,
    val userId: UUID,
    var text: String,
    val createdAt: String,
    val likes: Int = 0,
)
