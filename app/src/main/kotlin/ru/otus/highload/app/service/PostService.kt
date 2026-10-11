package ru.otus.highload.app.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.otus.highload.app.config.currentUserId
import ru.otus.highload.app.model.Post
import ru.otus.highload.app.repository.FriendRepository
import ru.otus.highload.app.repository.PostRepository
import ru.otus.highload.utils.uuidV7
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class PostService(
    private val postRepository: PostRepository,
    private val friendRepository: FriendRepository,
    private val feedCacheService: InMemoryFeedCacheService,
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

        // Фан-аут: пост видят те, кто добавил автора в друзья.
        val followers = friendRepository.findFollowerIds(userId)
        val score = createdAt.toEpochMilli()
        afterCommit {
            feedCacheService.addPostToFeeds(followers, postId, userId, score)
        }

        return postId
    }

    fun getPost(id: UUID): Post? {
        return postRepository.findById(id)
    }

    @Transactional
    fun updatePost(id: UUID, newText: String): Post? {
        val post = postRepository.findById(id) ?: return null
        val userId = currentUserId()

        if (post.userId != userId) {
            throw IllegalArgumentException("Not post owner")
        }

        post.text = newText
        postRepository.update(post)
        // Позиция поста в ленте определяется временем создания и не меняется при редактировании.
        return post
    }

    @Transactional
    fun deletePost(id: UUID) {
        val post = postRepository.findById(id) ?: return
        val userId = currentUserId()

        if (post.userId != userId) {
            throw IllegalArgumentException("Not post owner")
        }

        postRepository.deleteById(id)
        afterCommit {
            feedCacheService.removePostFromAllFeeds(id)
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
