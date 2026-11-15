package ru.otus.highload.utils.session

import org.springframework.data.redis.core.RedisOperations
import org.springframework.data.redis.core.SessionCallback
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.UUID

class RedisSessionStore(
    private val redis: StringRedisTemplate,
) : SessionStore {
    override fun create(token: UUID, userId: UUID) {
        redis.execute(object : SessionCallback<Unit?> {
            @Suppress("UNCHECKED_CAST")
            override fun <K : Any, V : Any> execute(operations: RedisOperations<K, V>): Unit? {
                val stringOps = operations as RedisOperations<String, String>
                stringOps.multi()
                stringOps.opsForValue().set(key(token), userId.toString())
                stringOps.opsForSet().add(SESSIONS_KEY, token.toString())
                stringOps.exec()
                return null
            }
        })
    }

    override fun resolveUserId(token: UUID): UUID? =
        redis.opsForValue().get(key(token))?.let(UUID::fromString)

    override fun delete(token: UUID) {
        redis.execute(object : SessionCallback<Unit?> {
            @Suppress("UNCHECKED_CAST")
            override fun <K : Any, V : Any> execute(operations: RedisOperations<K, V>): Unit? {
                val stringOps = operations as RedisOperations<String, String>
                stringOps.multi()
                stringOps.delete(key(token))
                stringOps.opsForSet().remove(SESSIONS_KEY, token.toString())
                stringOps.exec()
                return null
            }
        })
    }

    override fun count(): Long = redis.opsForSet().size(SESSIONS_KEY) ?: 0L

    private fun key(token: UUID) = "session:$token"

    private companion object {
        const val SESSIONS_KEY = "sessions"
    }
}
