package ru.otus.highload.app.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.otus.highload.app.repository.FriendRepository
import ru.otus.highload.app.repository.PostRepository
import java.util.UUID

@Service
class FriendService(
    private val friendRepository: FriendRepository,
    private val postRepository: PostRepository,
    private val userService: UserService,
    private val feedCacheService: InMemoryFeedCacheService,
) {

    @Transactional
    fun addFriend(userId: UUID, friendId: UUID) {
        val user = userService.getById(userId)
            ?: throw IllegalArgumentException("User not found: $userId")
        val friend = userService.getById(friendId)
            ?: throw IllegalArgumentException("Friend not found: $friendId")

        if (friend.id == user.id) {
            throw IllegalArgumentException("Cannot add yourself as friend")
        }

        friendRepository.save(user.id, friendId)

        val recentPosts = postRepository.findLatestByAuthor(friendId, feedCacheService.maxSize)
        afterCommit {
            feedCacheService.warmFeed(user.id, recentPosts)
        }
    }

    @Transactional
    fun deleteFriend(userId: UUID, friendId: UUID) {
        friendRepository.deleteByUserIdAndFriendId(userId, friendId)

        afterCommit {
            feedCacheService.removeAuthorFromFeed(userId, friendId)
        }
    }
}
