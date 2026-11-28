package ru.otus.highload.chat.repository

import com.fasterxml.jackson.core.type.TypeReference
import io.tarantool.client.box.TarantoolBoxClient
import org.springframework.stereotype.Component
import ru.otus.highload.chat.config.StorageUnavailableException
import ru.otus.highload.chat.model.DialogMessageState
import ru.otus.highload.chat.model.DialogSnapshot
import ru.otus.highload.utils.events.CounterEvent
import ru.otus.highload.utils.events.CounterEventType
import java.util.UUID
import java.util.concurrent.CompletionException

@Component
class TarantoolDialogEventStorage(
    private val client: TarantoolBoxClient,
) : DialogEventStorage {
    override fun outboxBatch(limit: Int): List<CounterEvent> = call {
        client.call(
            "dialog_outbox_batch",
            listOf(limit),
            object : TypeReference<List<List<Map<String, Any>>>>() {},
        ).join().get()
    }.single().map(::toEvent)

    override fun markPublished(eventId: UUID) {
        call {
            client.call("dialog_outbox_mark_published", listOf(eventId.toString()), String::class.java)
                .join().get()
        }.single()
    }

    override fun applyCompensation(event: CounterEvent): String = call {
        client.call(
            "dialog_apply_compensation",
            listOf(event.eventId, event.sagaId, event.messageId, event.version, event.type.name),
            String::class.java,
        ).join().get()
    }.single()

    override fun unreadSnapshot(userId: UUID): DialogSnapshot {
        val row = call {
            client.call(
                "dialog_unread_snapshot",
                listOf(userId.toString()),
                object : TypeReference<List<Map<String, Any>>>() {},
            ).join().get()
        }.single()
        @Suppress("UNCHECKED_CAST")
        val messages = (row["messages"] as? List<Map<String, Any>>).orEmpty().map {
            DialogMessageState(
                id = it.getValue("id").toString(),
                state = it.getValue("state").toString(),
                version = it.getValue("version").toString().toLong(),
            )
        }
        return DialogSnapshot(userId.toString(), row.getValue("count").toString().toLong(), messages)
    }

    private fun toEvent(row: Map<String, Any>) = CounterEvent(
        eventId = row.getValue("event_id").toString(),
        sagaId = row.getValue("saga_id").toString(),
        messageId = row.getValue("message_id").toString(),
        userId = row.getValue("user_id").toString(),
        version = row.getValue("version").toString().toLong(),
        type = CounterEventType.valueOf(row.getValue("type").toString()),
        occurredAtNs = row.getValue("occurred_at_ns").toString().longValue(),
    )

    private fun String.longValue(): Long = removeSuffix("ULL").removeSuffix("LL").toLong()

    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (ex: CompletionException) {
        throw StorageUnavailableException("Tarantool unavailable", ex.cause ?: ex)
    }
}
