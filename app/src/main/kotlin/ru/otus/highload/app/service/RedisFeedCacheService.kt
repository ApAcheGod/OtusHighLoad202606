package ru.otus.highload.app.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RedisFeedCacheService(
    @Value("\${app.feed.max-size:1000}") override val maxSize: Int,
    @Value("\${app.feed.ttl-hours:24}") ttlHours: Long,
    private val redis: StringRedisTemplate,
    meterRegistry: MeterRegistry,
) : FeedCacheService {
    private val ttlSeconds = ttlHours * 3600
    private val hits = Counter.builder("feed.cache.hits").register(meterRegistry)
    private val misses = Counter.builder("feed.cache.misses").register(meterRegistry)
    private val trims = Counter.builder("feed.cache.trims").register(meterRegistry)
    private val addScript = script("redis/feed_add.lua")
    private val warmScript = script("redis/feed_warm.lua")
    private val removeScript = script("redis/feed_remove.lua")
    private val removeAuthorScript = script("redis/feed_remove_author.lua")

    override fun addPost(feedOwnerId: UUID, postId: UUID, authorId: UUID, score: Long): Long {
        val trimmed = redis.execute(
            addScript, keys(feedOwnerId), postId.toString(), authorId.toString(),
            score.toString(), maxSize.toString(), ttlSeconds.toString(),
        ) ?: 0L
        trims.increment(trimmed.toDouble())
        return score
    }

    override fun warmFeed(feedOwnerId: UUID, items: Collection<FeedItemData>) {
        val args = mutableListOf(maxSize.toString(), ttlSeconds.toString())
        items.forEach {
            args += it.postId.toString()
            args += it.authorId.toString()
            args += it.score.toString()
        }
        val trimmed = redis.execute(warmScript, keys(feedOwnerId), *args.toTypedArray()) ?: 0L
        trims.increment(trimmed.toDouble())
    }

    override fun removePostFromFeed(feedOwnerId: UUID, postId: UUID, score: Long?): Boolean =
        (redis.execute(removeScript, keys(feedOwnerId), postId.toString()) ?: 0L) > 0

    override fun removeAuthorFromFeed(feedOwnerId: UUID, authorId: UUID) {
        redis.execute(removeAuthorScript, keys(feedOwnerId), authorId.toString())
    }

    override fun getFeedPostIds(feedOwnerId: UUID, offset: Int, limit: Int): List<UUID>? {
        val keySet = keys(feedOwnerId)
        if (redis.hasKey(keySet[2]) != true) {
            misses.increment()
            return null
        }
        val result = redis.opsForZSet()
            .reverseRange(keySet[0], offset.toLong(), offset.toLong() + limit - 1)
            .orEmpty().map(UUID::fromString)
        keySet.forEach { redis.expire(it, java.time.Duration.ofSeconds(ttlSeconds)) }
        hits.increment()
        return result
    }

    private fun keys(userId: UUID) = listOf(
        "feed:$userId:posts", "feed:$userId:authors", "feed:$userId:ready",
    )

    private fun script(path: String) = DefaultRedisScript<Long>().apply {
        setLocation(ClassPathResource(path))
        resultType = Long::class.java
    }
}
