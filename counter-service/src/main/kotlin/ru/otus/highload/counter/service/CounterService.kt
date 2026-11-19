package ru.otus.highload.counter.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import ru.otus.highload.counter.config.CounterMetrics
import ru.otus.highload.counter.projection.CounterProjection
import java.time.Duration
import java.util.UUID

@Service
class CounterService(
    private val projection: CounterProjection,
    private val snapshotClient: ChatSnapshotClient,
    private val redis: StringRedisTemplate,
    private val metrics: CounterMetrics,
    @Value("\${app.counter.restore-lock-ms:5000}") private val lockMs: Long = 5_000,
) {
    private val releaseLockScript = DefaultRedisScript<Long>().apply {
        setLocation(ClassPathResource("valkey/release_restore_lock.lua"))
        setResultType(Long::class.java)
    }

    fun getUnread(userId: UUID): Long = projection.getUnread(userId) ?: restoreWithLock(userId, force = false)

    fun reconcile(userId: UUID): Long = restoreWithLock(userId, force = true)

    private fun restoreWithLock(userId: UUID, force: Boolean): Long {
        val deadline = System.nanoTime() + Duration.ofMillis(lockMs).toNanos()
        while (System.nanoTime() < deadline) {
            val token = UUID.randomUUID().toString()
            val lockKey = "unread:{$userId}:restore-lock"
            val acquired = redis.opsForValue().setIfAbsent(lockKey, token, Duration.ofMillis(lockMs)) == true
            if (acquired) {
                try {
                    if (!force) {
                        projection.getUnread(userId)?.let { return it }
                    }
                    return projection.restore(snapshotClient.snapshot(userId)).also { metrics.restored() }
                } finally {
                    redis.execute(releaseLockScript, listOf(lockKey), token)
                }
            }

            projection.getUnread(userId)?.let {
                if (!force) return it
            }
            sleepBriefly()
        }
        projection.getUnread(userId)?.let {
            if (!force) return it
        }
        throw CounterRestoreTimeoutException("Timed out restoring counter for $userId")
    }

    private fun sleepBriefly() {
        try {
            Thread.sleep(25)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CounterRestoreTimeoutException("Interrupted while restoring counter", ex)
        }
    }
}

class CounterRestoreTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
