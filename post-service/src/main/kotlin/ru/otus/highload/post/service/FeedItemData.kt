package ru.otus.highload.post.service

import java.util.UUID

data class FeedItemData(val postId: UUID, val authorId: UUID, val score: Long)
