package ru.otus.highload.feed.service

import java.util.UUID

data class FeedItemData(val postId: UUID, val authorId: UUID, val score: Long)

interface FeedCacheService {
    val maxSize: Int
    fun addPost(feedOwnerId: UUID, postId: UUID, authorId: UUID, score: Long): Long
    fun removePostFromFeed(feedOwnerId: UUID, postId: UUID, score: Long? = null): Boolean
    fun removeAuthorFromFeed(feedOwnerId: UUID, authorId: UUID)
    fun warmFeed(feedOwnerId: UUID, items: Collection<FeedItemData>)
    fun getFeedPostIds(feedOwnerId: UUID, offset: Int, limit: Int): List<UUID>?
}
