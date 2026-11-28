package ru.otus.highload.counter.projection

import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import ru.otus.highload.counter.model.CounterSnapshot
import ru.otus.highload.utils.events.CounterEvent
import java.util.UUID

@Component
class ValkeyCounterProjection(
    private val redis: StringRedisTemplate,
) : CounterProjection {
    private val sentScript = redisScript("valkey/apply_sent.lua")
    private val readScript = redisScript("valkey/apply_read.lua")
    private val restoreScript = DefaultRedisScript<Long>().apply {
        setLocation(ClassPathResource("valkey/restore_counter.lua"))
        setResultType(Long::class.java)
    }

    override fun applySent(event: CounterEvent): ApplyResult = apply(sentScript, event)

    override fun applyRead(event: CounterEvent): ApplyResult = apply(readScript, event)

    override fun getUnread(userId: UUID): Long? =
        redis.opsForValue().get(counterKey(userId.toString()))?.toLong()

    override fun restore(snapshot: CounterSnapshot): Long {
        val keys = listOf(counterKey(snapshot.userId)) + snapshot.messages.map {
            "unread:{${snapshot.userId}}:message:${it.id}"
        }
        val args = listOf(snapshot.count.toString()) + snapshot.messages.flatMap {
            listOf(it.state, it.version.toString())
        }
        return redis.execute(restoreScript, keys, *args.toTypedArray())
            ?: error("Valkey returned no restore result")
    }

    private fun apply(script: DefaultRedisScript<List<*>>, event: CounterEvent): ApplyResult {
        val result = redis.execute(
            script,
            keys(event),
            event.version.toString(),
            event.eventId,
            event.sagaId,
            System.currentTimeMillis().toString(),
            event.messageId,
        ) ?: error("Valkey returned no projection result")
        return ApplyResult.valueOf(result.first().toString())
    }

    private fun keys(event: CounterEvent): List<String> {
        val tag = "{${event.userId}}"
        return listOf(
            "unread:$tag:counter",
            "unread:$tag:message:${event.messageId}",
            "unread:$tag:saga:${event.sagaId}",
        )
    }

    private fun counterKey(userId: String) = "unread:{$userId}:counter"

    private fun redisScript(path: String) = DefaultRedisScript<List<*>>().apply {
        setLocation(ClassPathResource(path))
        setResultType(List::class.java)
    }
}
