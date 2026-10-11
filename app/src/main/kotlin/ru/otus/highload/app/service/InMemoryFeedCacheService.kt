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
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/** Данные для прогрева ленты из БД. */
data class FeedItemData(
    val postId: UUID,
    val authorId: UUID,
    val score: Long,
)

/** Запись обратного индекса: userId + timestamp. */
private data class IndexEntry(val userId: UUID, val timestamp: Long)

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

@Service
class InMemoryFeedCacheService(
    @Value("\${app.feed.max-size:1000}")
    val maxSize: Int,
    @Value("\${app.feed.ttl-hours:24}")
    private val ttlHours: Long,
    @Value("\${app.feed.fanout.threads:0}") // 0 = default virtual threads
    private val fanoutThreads: Int,
    meterRegistry: MeterRegistry
) {

    private val log = LoggerFactory.getLogger(InMemoryFeedCacheService::class.java)

    // ==================== FIELDS (must be before init) ====================

    // UUID как ключ — естественный идентификатор
    private val feeds = ConcurrentHashMap<UUID, ConcurrentSkipListMap<Long, FeedEntry>>()
    // postId → Set<IndexEntry> — обратный индекс для O(1) удаления
    private val postIndex = ConcurrentHashMap<UUID, MutableSet<IndexEntry>>()
    // TTL очередь: O(1) expiry вместо сканирования всех юзеров
    private val ttlQueue = DelayQueue<TtlEntry>()
    private val lastAccess = ConcurrentHashMap<UUID, Long>()
    private val timestampGenerator = AtomicLong(System.currentTimeMillis())

    // Background trimmer — не блокирует запись
    private val trimExecutor = Executors.newSingleThreadScheduledExecutor()
    private val pendingTrims = ConcurrentHashMap<UUID, ConcurrentSkipListMap<Long, FeedEntry>>()

    // Custom executor для фанаута (Virtual Threads friendly)
    private val fanoutExecutor: ExecutorService = if (fanoutThreads > 0)
        Executors.newScheduledThreadPool(fanoutThreads)
    else
        Executors.newVirtualThreadPerTaskExecutor()

    // Метрики
    private val cacheHits = Counter.builder("feed.cache.hits").register(meterRegistry)
    private val cacheMisses = Counter.builder("feed.cache.misses").register(meterRegistry)
    private val trimCounter = Counter.builder("feed.cache.trims").register(meterRegistry)
    private val addPostTimer = Timer.builder("feed.cache.add_post.duration").publishPercentileHistogram().register(meterRegistry)
    private val getFeedTimer = Timer.builder("feed.cache.get_feed.duration").publishPercentileHistogram().register(meterRegistry)
    private val removePostTimer = Timer.builder("feed.cache.remove_post.duration").publishPercentileHistogram().register(meterRegistry)
    private val fanoutTimer = Timer.builder("feed.cache.fanout.duration").publishPercentileHistogram().register(meterRegistry)
    private val warmFeedTimer = Timer.builder("feed.cache.warm_feed.duration").publishPercentileHistogram().register(meterRegistry)

    init {
        Gauge.builder("feed.cache.total_feeds", feeds) { it.size.toDouble() }.register(meterRegistry)
        Gauge.builder("feed.cache.total_posts", feeds) { it -> it.values.sumOf { it.size }.toDouble() }.register(meterRegistry)
        Gauge.builder("feed.cache.fanout.queue_size", pendingTrims) { it.size.toDouble() }.register(meterRegistry)
        Gauge.builder("feed.cache.ttl_queue_size", ttlQueue) { it.size.toDouble() }.register(meterRegistry)

        // Background trimmer: каждые 100мс обрабатываем накопленные триммы
        trimExecutor.scheduleAtFixedRate({ processPendingTrims() }, 100, 100, TimeUnit.MILLISECONDS)
    }

    // ==================== PUBLIC API ====================

    /** Добавить пост в ленту конкретного пользователя. */
    fun addPost(feedOwnerId: UUID, postId: UUID, authorId: UUID, score: Long): Long =
        addPostTimer.recordCallable {
            addPostInternal(feedOwnerId, postId, authorId, score)
        }

    /** Добавить пост в ленты множества пользователей (фан-аут). */
    fun addPostToFeeds(feedOwnerIds: Collection<UUID>, postId: UUID, authorId: UUID, score: Long): List<Long> =
        fanoutTimer.recordCallable {
            val timestamp = generateTimestamp()
            if (feedOwnerIds.size <= 1) {
                // Маленький фанаут — синхронно
                feedOwnerIds.map { ownerId -> addPostInternal(ownerId, postId, authorId, timestamp) }
            } else {
                // Большой фанаут — параллельно через custom executor
                feedOwnerIds.parallelStream().map { ownerId ->
                    addPostInternal(ownerId, postId, authorId, timestamp)
                }.toList()
            }
        }

    /** Удалить пост из всех лент. */
    fun removePostFromAllFeeds(postId: UUID): Int =
        removePostTimer.recordCallable {
            postIndex.remove(postId)?.let { entries ->
                entries.forEach { entry ->
                    feeds[entry.userId]?.remove(entry.timestamp)
                }
                entries.size
            } ?: 0
        }

    /** Удалить все посты автора из ленты пользователя. */
    fun removeAuthorFromFeed(feedOwnerId: UUID, authorId: UUID) {
        val feed = feeds[feedOwnerId] ?: return
        val toRemove = mutableListOf<Long>()
        feed.forEach { (ts, entry) ->
            if (entry.authorId == authorId) {
                toRemove.add(ts)
            }
        }
        toRemove.forEach { timestamp ->
            val entry = feed.remove(timestamp)!!
            removeFromPostIndex(entry.postId, feedOwnerId, timestamp)
        }
        log.debug("removeAuthorFromFeed: userId={}, authorId={}, removed={}", feedOwnerId, authorId, toRemove.size)
    }

    /**
     * Возвращает id постов ленты (новые впереди) либо null, если лента ещё
     * не прогрета (нет в кеше) — вызывающая сторона должна сделать прогрев из БД.
     */
    fun getFeedPostIds(feedOwnerId: UUID, offset: Int, limit: Int): List<UUID>? =
        getFeedTimer.recordCallable {
            updateLastAccess(feedOwnerId)
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
    fun warmFeed(feedOwnerId: UUID, items: Collection<FeedItemData>): Unit? =
        warmFeedTimer.recordCallable {
            val feed = feeds.computeIfAbsent(feedOwnerId) { ConcurrentSkipListMap() }
            for (item in items) {
                // Атомарно: putIfAbsent избегает race condition
                feed.putIfAbsent(item.score, FeedEntry(item.postId, item.authorId))
                postIndex.computeIfAbsent(item.postId) { ConcurrentHashMap.newKeySet() }
                    .add(IndexEntry(feedOwnerId, item.score))
            }
            scheduleTrim(feedOwnerId, feed)
            updateLastAccess(feedOwnerId)
        }

    /** Размер ленты пользователя. */
    fun feedSize(feedOwnerId: UUID): Int = feeds[feedOwnerId]?.size ?: 0

    // ==================== HEALTH ====================

    fun health(): Health {
        val totalFeeds = feeds.size
        val totalPosts = feeds.values.sumOf { it.size }
        val ttlQueueSize = ttlQueue.size
        val pendingTrimsSize = pendingTrims.size

        return Health.up()
            .withDetail("total_feeds", totalFeeds)
            .withDetail("total_posts", totalPosts)
            .withDetail("ttl_queue_size", ttlQueueSize)
            .withDetail("pending_trims", pendingTrimsSize)
            .withDetail("max_feed_size", maxSize)
            .withDetail("ttl_hours", ttlHours)
            .build()
    }

    // ==================== INTERNAL ====================

    private fun addPostInternal(feedOwnerId: UUID, postId: UUID, authorId: UUID, timestamp: Long): Long {
        val feed = feeds.getOrPut(feedOwnerId) { ConcurrentSkipListMap() }
        feed[timestamp] = FeedEntry(postId, authorId)
        postIndex.computeIfAbsent(postId) { ConcurrentHashMap.newKeySet() }
            .add(IndexEntry(feedOwnerId, timestamp))
        scheduleTrim(feedOwnerId, feed)
        updateLastAccess(feedOwnerId)
        log.trace("addPost: userId={}, postId={}, authorId={}, timestamp={}", feedOwnerId, postId, authorId, timestamp)
        return timestamp
    }

    private fun scheduleTrim(feedOwnerId: UUID, feed: ConcurrentSkipListMap<Long, FeedEntry>) {
        if (feed.size > maxSize) {
            pendingTrims[feedOwnerId] = feed
        }
    }

    private fun processPendingTrims() {
        pendingTrims.forEach { (feedOwnerId, feed) ->
            if (feed.size > maxSize) {
                trimFeed(feedOwnerId, feed)
            } else {
                pendingTrims.remove(feedOwnerId)
            }
        }
    }

    private fun trimFeed(feedOwnerId: UUID, feed: ConcurrentSkipListMap<Long, FeedEntry>) {
        while (feed.size > maxSize) {
            feed.pollFirstEntry()?.let { (ts, entry) ->
                removeFromPostIndex(entry.postId, feedOwnerId, ts)
                trimCounter.increment()
            }
        }
        if (feed.size <= maxSize) {
            pendingTrims.remove(feedOwnerId)
        }
    }

    private fun removeFromPostIndex(postId: UUID, userId: UUID, timestamp: Long) {
        val entries = postIndex[postId]
        if (entries != null) {
            // Iterator-based removal — без аллокации List
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.userId == userId && entry.timestamp == timestamp) {
                    iterator.remove()
                    break
                }
            }
            if (entries.isEmpty()) {
                postIndex.remove(postId)
            }
        }
    }

    private fun updateLastAccess(userId: UUID) {
        val now = System.currentTimeMillis()
        val prev = lastAccess.put(userId, now)
        // Перепланируем TTL: старый TtlEntry станет obsolete, новый встанет в очередь
        ttlQueue.add(TtlEntry(userId, now + ttlHours * 3600 * 1000))
        if (prev != null) {
            log.trace("updateLastAccess: userId={}, prev={}, now={}", userId, prev, now)
        }
    }

    @Scheduled(fixedRateString = "\${app.feed.cleanup-interval-ms:60000}") // чаще, но O(1) через DelayQueue
    fun cleanupInactiveUsers() {
        val now = System.currentTimeMillis()
        var processed = 0
        while (true) {
            val entry = ttlQueue.poll()
            if (entry == null || entry.expireAt > now) {
                if (entry != null) ttlQueue.put(entry) // положить обратно
                break
            }
            val userId = entry.userId
            // Double-check: ещё актуально?
            val last = lastAccess[userId]
            if (last != null && now - last > ttlHours * 3600 * 1000) {
                removeUserFeed(userId)
                processed++
            }
        }
        if (processed > 0) {
            log.debug("cleanupInactiveUsers: removed {} inactive users", processed)
        }
    }

    private fun removeUserFeed(userId: UUID) {
        feeds.remove(userId)?.forEach { (ts, entry) ->
            removeFromPostIndex(entry.postId, userId, ts)
        }
        lastAccess.remove(userId)
        pendingTrims.remove(userId)
    }

    // Монотонные таймстемпы без коллизий
    private fun generateTimestamp(): Long =
        timestampGenerator.updateAndGet { current ->
            val next = System.currentTimeMillis()
            if (next > current) next else current + 1
        }

    // Graceful shutdown
    fun shutdown() {
        trimExecutor.shutdown()
        fanoutExecutor.shutdown()
        try {
            if (!trimExecutor.awaitTermination(5, TimeUnit.SECONDS)) trimExecutor.shutdownNow()
            if (!fanoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) fanoutExecutor.shutdownNow()
        } catch (e: InterruptedException) {
            trimExecutor.shutdownNow()
            fanoutExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}

@Configuration
class FeedCacheHealthConfig {

    @Bean
    fun feedCacheHealthIndicator(service: InMemoryFeedCacheService): HealthIndicator {
        return HealthIndicator { service.health() }
    }
}