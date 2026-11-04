package ru.otus.highload.utils.events

enum class PostEventOperation { CREATED, DELETED }

data class PostEvent(
    val operation: PostEventOperation,
    val postId: String,
    val authorId: String,
    val text: String? = null,
    val createdAtMs: Long,
)

enum class FeedUserOperation { POSTED, REMOVED }

data class FeedUserEvent(
    val operation: FeedUserOperation,
    val postId: String,
    val postText: String? = null,
    val author_user_id: String? = null,
    val createdAtMs: Long? = null,
)
