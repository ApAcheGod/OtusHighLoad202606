package ru.otus.highload.app.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.otus.highload.app.config.currentUserId
import ru.otus.highload.app.messaging.PostEventProducer
import ru.otus.highload.app.model.Post
import ru.otus.highload.app.repository.PostRepository
import ru.otus.highload.utils.events.PostEvent
import ru.otus.highload.utils.events.PostEventOperation
import ru.otus.highload.utils.uuidV7
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class PostService(
    private val postRepository: PostRepository,
    private val feedCacheService: InMemoryFeedCacheService,
    private val postEventProducer: PostEventProducer,
) {

    @Transactional
    fun createPost(text: String): UUID {
        val userId = currentUserId()
        val postId = uuidV7()
        val createdAt = Instant.now()
        val post = Post(
            id = postId,
            userId = userId,
            text = text,
            createdAt = DateTimeFormatter.ISO_INSTANT.format(createdAt),
            likes = 0,
        )
        postRepository.save(post)

        // Отложенная материализация: фан-аут по друзьям, запись в ленты и доставка
        // WS-событий — асинхронно через Kafka
        val score = createdAt.toEpochMilli()
        afterCommit {
            postEventProducer.postEvent(
                PostEvent(PostEventOperation.CREATED, postId.toString(), userId.toString(), text, score)
            )
        }

        return postId
    }

    fun getPost(id: UUID): Post? {
        return postRepository.findById(id)
    }

    @Transactional
    fun updatePost(id: UUID, newText: String): Post? {
        val userId = currentUserId()
        postRepository.update(id, userId, newText)?.let { return it }
        postRepository.findById(id)?.let { post ->
            if (post.userId != userId) {
                throw IllegalArgumentException("Not post owner")
            }
        }
        return null
    }

    @Transactional
    fun deletePost(id: UUID) {
        val userId = currentUserId()
        val post = postRepository.findById(id) ?: return
        if (post.userId != userId) {
            throw IllegalArgumentException("Not post owner")
        }
        if (postRepository.deleteById(id, userId)) {
            val score = Instant.parse(post.createdAt).toEpochMilli()
            afterCommit {
                postEventProducer.postEvent(
                    PostEvent(
                        PostEventOperation.DELETED, id.toString(), userId.toString(),
                        createdAtMs = score,
                    )
                )
            }
        }
    }

    fun getFeed(offset: Int, limit: Int): List<Post> {
        require(offset >= 0) { "offset must be >= 0" }
        require(limit >= 1) { "limit must be >= 1" }

        val userId = currentUserId()

        // Промах кеша (restart / первый запрос) → прогреваем ленту из БД.
        if (feedCacheService.getFeedPostIds(userId, 0, 1) == null) {
            warmFeed(userId)
        }

        val postIds = feedCacheService.getFeedPostIds(userId, offset, limit)
            ?: return emptyList()
        if (postIds.isEmpty()) return emptyList()

        val posts = postRepository.findByIds(postIds)
        val postMap = posts.associateBy { it.id }
        return postIds.mapNotNull { postMap[it] }
    }

    private fun warmFeed(userId: UUID) {
        val candidates = postRepository.findFriendFeedCandidates(userId, feedCacheService.maxSize)
        feedCacheService.warmFeed(userId, candidates)
    }
}
