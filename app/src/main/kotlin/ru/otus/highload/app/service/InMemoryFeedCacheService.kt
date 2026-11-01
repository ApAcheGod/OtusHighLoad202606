package ru.otus.highload.app.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.LongAdder
import kotlin.math.min

/** Данные для прогрева ленты из БД. */
data class FeedItemData(
    val postId: UUID,
    val authorId: UUID,
    val score: Long,
)

/** Посты с одинаковым score не перезаписывают друг друга; postId — детерминированный tie-breaker. */
private data class FeedKey(val score: Long, val postId: UUID) : Comparable<FeedKey> {
    private val postIdKey: String = postId.toString()

    override fun compareTo(other: FeedKey): Int =
        score.compareTo(other.score).takeIf { it != 0 }
            ?: postIdKey.compareTo(other.postIdKey)
}

/** Запись обратного индекса: userId + составной ключ ленты. */
private data class IndexEntry(val userId: UUID, val key: FeedKey)

/** Запись в ленте: postId + authorId (нужен для removeAuthorFromFeed). */
private data class FeedEntry(val postId: UUID, val authorId: UUID)

/** Элемент TTL очереди с задержкой. */
private class TtlEntry(
    val userId: UUID,
    val expireAt: Long
) : Delayed {
    override fun getDelay(unit: TimeUnit): Long = unit.convert(expireAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
    override fun compareTo(other: Delayed): Int = this.expireAt.compareTo((other as TtlEntry).expireAt)
}

/**
 * TTL-трекер активности: DelayQueue + lastAccess, вытеснение неактивных лент.
 * DelayQueue даёт O(1) expiry вместо сканирования всех пользователей.
 */
private class FeedTtlTracker(private val ttlHours: Long) {
    private val ttlQueue = DelayQueue<TtlEntry>()
    private val lastAccess = ConcurrentHashMap<UUID, Long>()

    private val ttlMs: Long get() = ttlHours * 3600 * 1000

    val queueSize: Int get() = ttlQueue.size

    fun update(userId: UUID) {
        val now = System.currentTimeMillis()
        val prev = lastAccess.put(userId, now)
        if (prev == null || prev + ttlMs <= now) {
            ttlQueue.add(TtlEntry(userId, now + ttlMs))
        }
    }

    fun remove(userId: UUID) {
        lastAccess.remove(userId)
    }

    /**
     * Вытесняет ленты, неактивные дольше TTL. Принимает колбэк удаления ленты,
     * чтобы не владеть состоянием кеша. Возвращает число вытесненных пользователей.
     */
    fun cleanupExpired(now: Long, removeFeed: (UUID) -> Unit): Int {
        var processed = 0
        while (true) {
            val entry = ttlQueue.poll()
            if (entry == null || entry.expireAt > now) {
                if (entry != null) ttlQueue.put(entry) // положить обратно
                break
            }
            val userId = entry.userId
            val last = lastAccess[userId]
            if (last != null && now - last > ttlMs) {
                removeFeed(userId)
                processed++
            } else if (last != null) {
                ttlQueue.add(TtlEntry(userId, last + ttlMs))
            }
        }
        return processed
    }
}

/**
 * Фоновый триммер переполненных лент: записи только планируют тримм,
 * само вытеснение идёт в одном потоке раз в 100 мс и не блокирует запись.
 */
private class FeedTrimmer(
    private val maxSize: Int,
    private val onEntryEvicted: (UUID, FeedKey, FeedEntry) -> Unit,
    private val onTrimmed: () -> Unit,
) {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val pendingTrims = ConcurrentHashMap<UUID, ConcurrentSkipListMap<FeedKey, FeedEntry>>()

    val pendingCount: Int get() = pendingTrims.size

    init {
        executor.scheduleAtFixedRate({ processPending() }, 100, 100, TimeUnit.MILLISECONDS)
    }

    fun schedule(feedOwnerId: UUID, feed: ConcurrentSkipListMap<FeedKey, FeedEntry>) {
        if (feed.size > maxSize) {
            pendingTrims[feedOwnerId] = feed
        }
    }

    fun remove(userId: UUID) {
        pendingTrims.remove(userId)
    }

    private fun processPending() {
        pendingTrims.forEach { (feedOwnerId, feed) ->
            if (feed.size > maxSize) {
                trimFeed(feedOwnerId, feed)
            } else {
                pendingTrims.remove(feedOwnerId)
            }
        }
    }

    private fun trimFeed(feedOwnerId: UUID, feed: ConcurrentSkipListMap<FeedKey, FeedEntry>) {
        while (feed.size > maxSize) {
            feed.pollFirstEntry()?.let { (key, entry) ->
                onEntryEvicted(feedOwnerId, key, entry)
                onTrimmed()
            }
        }
        if (feed.size <= maxSize) {
            pendingTrims.remove(feedOwnerId)
        }
    }

    fun shutdown() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow()
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}

@Service
class InMemoryFeedCacheService(
    @Value("\${app.feed.max-size:1000}")
    val maxSize: Int,
    @Value("\${app.feed.ttl-hours:24}")
    private val ttlHours: Long,
    meterRegistry: MeterRegistry
) {

    private val log = LoggerFactory.getLogger(InMemoryFeedCacheService::class.java)

    private val feeds = ConcurrentHashMap<UUID, ConcurrentSkipListMap<FeedKey, FeedEntry>>()
    private val postIndex = ConcurrentHashMap<UUID, MutableSet<IndexEntry>>()

    private val totalPosts = LongAdder()

    private val ttlTracker = FeedTtlTracker(ttlHours)
    private val trimmer = FeedTrimmer(
        maxSize = maxSize,
        onEntryEvicted = { feedOwnerId, key, entry -> evictFromIndexes(feedOwnerId, key, entry) },
        onTrimmed = { trimCounter.increment() },
    )

    private val cacheHits = Counter.builder("feed.cache.hits").register(meterRegistry)
    private val cacheMisses = Counter.builder("feed.cache.misses").register(meterRegistry)
    private val trimCounter = Counter.builder("feed.cache.trims").register(meterRegistry)
    private val addPostTimer = Timer.builder("feed.cache.add_post.duration").publishPercentileHistogram().register(meterRegistry)
    private val getFeedTimer = Timer.builder("feed.cache.get_feed.duration").publishPercentileHistogram().register(meterRegistry)
    private val removePostTimer = Timer.builder("feed.cache.remove_post.duration").publishPercentileHistogram().register(meterRegistry)
    private val warmFeedTimer = Timer.builder("feed.cache.warm_feed.duration").publishPercentileHistogram().register(meterRegistry)

    init {
        Gauge.builder("feed.cache.total_feeds", feeds) { it.size.toDouble() }.register(meterRegistry)
        Gauge.builder("feed.cache.total_posts", totalPosts) { it.sum().toDouble() }.register(meterRegistry)
        Gauge.builder("feed.cache.fanout.queue_size", trimmer) { it.pendingCount.toDouble() }.register(meterRegistry)
        Gauge.builder("feed.cache.ttl_queue_size", ttlTracker) { it.queueSize.toDouble() }.register(meterRegistry)
    }

    // ==================== PUBLIC API ====================

    /** Добавить пост в ленту конкретного пользователя. */
    fun addPost(feedOwnerId: UUID, postId: UUID, authorId: UUID, score: Long): Long =
        addPostTimer.recordCallable {
            addPostInternal(feedOwnerId, postId, authorId, score)
        }

    /** Удалить пост из ленты конкретного пользователя (персональное событие REMOVED). */
    fun removePostFromFeed(feedOwnerId: UUID, postId: UUID, score: Long? = null): Boolean =
        removePostTimer.recordCallable {
            val feed = feeds[feedOwnerId] ?: return@recordCallable false
            if (score != null && feed.remove(FeedKey(score, postId)) != null) {
                totalPosts.decrement()
                removeFromPostIndex(postId, feedOwnerId, FeedKey(score, postId))
                return@recordCallable true
            }
            val keys = feed.entries.filter { it.value.postId == postId }.map { it.key }
            keys.forEach { key ->
                feed.remove(key)?.let {
                    totalPosts.decrement()
                    removeFromPostIndex(it.postId, feedOwnerId, key)
                }
            }
            keys.isNotEmpty()
        }

    /** Удалить все посты автора из ленты пользователя. */
    fun removeAuthorFromFeed(feedOwnerId: UUID, authorId: UUID) {
        val feed = feeds[feedOwnerId] ?: return
        val toRemove = mutableListOf<FeedKey>()
        feed.forEach { (key, entry) ->
            if (entry.authorId == authorId) {
                toRemove.add(key)
            }
        }
        toRemove.forEach { key ->
            feed.remove(key)?.let { entry ->
                totalPosts.decrement()
                removeFromPostIndex(entry.postId, feedOwnerId, key)
            }
        }
        log.debug("removeAuthorFromFeed: userId={}, authorId={}, removed={}", feedOwnerId, authorId, toRemove.size)
    }

    /**
     * Возвращает id постов ленты (новые впереди) либо null, если лента ещё
     * не прогрета (нет в кеше) — вызывающая сторона должна сделать прогрев из БД.
     */
    fun getFeedPostIds(feedOwnerId: UUID, offset: Int, limit: Int): List<UUID>? =
        getFeedTimer.recordCallable {
            ttlTracker.update(feedOwnerId)
            val feed = feeds[feedOwnerId]
            if (feed == null) {
                cacheMisses.increment()
                null
            } else {
                val size = feed.size
                val effectiveLimit = min(limit, size - offset)
                if (effectiveLimit <= 0) {
                    cacheMisses.increment()
                    emptyList()
                } else {
                    val result = ArrayList<UUID>(effectiveLimit)
                    var skipped = 0
                    for (entry in feed.descendingMap().values) {
                        if (skipped < offset) {
                            skipped++
                            continue
                        }
                        result.add(entry.postId)
                        if (result.size == effectiveLimit) break
                    }
                    if (result.isEmpty()) cacheMisses.increment() else cacheHits.increment()
                    result
                }
            }
        }

    /** Прогрев ленты из БД. Пустой список тоже создаёт запись (лента «прогрета»). */
    fun warmFeed(feedOwnerId: UUID, items: Collection<FeedItemData>) {
        warmFeedTimer.record(Runnable {
            val feed = feeds.computeIfAbsent(feedOwnerId) { ConcurrentSkipListMap() }
            for (item in items) {
                val key = FeedKey(item.score, item.postId)
                if (feed.putIfAbsent(key, FeedEntry(item.postId, item.authorId)) == null) {
                    totalPosts.increment()
                }
                postIndex.computeIfAbsent(item.postId) { ConcurrentHashMap.newKeySet() }
                    .add(IndexEntry(feedOwnerId, key))
            }
            trimmer.schedule(feedOwnerId, feed)
            ttlTracker.update(feedOwnerId)
        })
    }

    // ==================== HEALTH ====================

    fun health(): Health {
        val totalFeeds = feeds.size
        val totalPostsInFeeds = feeds.values.sumOf { it.size }

        return Health.up()
            .withDetail("total_feeds", totalFeeds)
            .withDetail("total_posts", totalPostsInFeeds)
            .withDetail("ttl_queue_size", ttlTracker.queueSize)
            .withDetail("pending_trims", trimmer.pendingCount)
            .withDetail("max_feed_size", maxSize)
            .withDetail("ttl_hours", ttlHours)
            .build()
    }

    // ==================== INTERNAL ====================

    private fun addPostInternal(feedOwnerId: UUID, postId: UUID, authorId: UUID, score: Long): Long {
        val key = FeedKey(score, postId)
        val feed = feeds.computeIfAbsent(feedOwnerId) { ConcurrentSkipListMap() }
        if (feed.putIfAbsent(key, FeedEntry(postId, authorId)) == null) {
            totalPosts.increment()
        }
        postIndex.computeIfAbsent(postId) { ConcurrentHashMap.newKeySet() }
            .add(IndexEntry(feedOwnerId, key))
        trimmer.schedule(feedOwnerId, feed)
        ttlTracker.update(feedOwnerId)
        log.trace("addPost: userId={}, postId={}, authorId={}, score={}", feedOwnerId, postId, authorId, score)
        return score
    }

    private fun evictFromIndexes(feedOwnerId: UUID, key: FeedKey, entry: FeedEntry) {
        totalPosts.decrement()
        removeFromPostIndex(entry.postId, feedOwnerId, key)
    }

    private fun removeFromPostIndex(postId: UUID, userId: UUID, key: FeedKey) {
        val entries = postIndex[postId]
        if (entries != null) {
            // Iterator-based removal — без аллокации List
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.userId == userId && entry.key == key) {
                    iterator.remove()
                    break
                }
            }
            if (entries.isEmpty()) {
                postIndex.remove(postId, entries)
            }
        }
    }

    @Scheduled(fixedRateString = "\${app.feed.cleanup-interval-ms:60000}") // чаще, но O(1) через DelayQueue
    fun cleanupInactiveUsers() {
        val processed = ttlTracker.cleanupExpired(System.currentTimeMillis(), ::removeUserFeed)
        if (processed > 0) {
            log.debug("cleanupInactiveUsers: removed {} inactive users", processed)
        }
    }

    private fun removeUserFeed(userId: UUID) {
        feeds.remove(userId)?.forEach { (key, entry) ->
            totalPosts.decrement()
            removeFromPostIndex(entry.postId, userId, key)
        }
        ttlTracker.remove(userId)
        trimmer.remove(userId)
    }

    // Graceful shutdown
    fun shutdown() {
        trimmer.shutdown()
    }
}

@Configuration
class FeedCacheHealthConfig {

    @Bean
    fun feedCacheHealthIndicator(service: InMemoryFeedCacheService): HealthIndicator {
        return HealthIndicator { service.health() }
    }
}
