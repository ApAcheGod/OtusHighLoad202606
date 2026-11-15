package ru.otus.highload.chat.repository

import com.fasterxml.jackson.core.type.TypeReference
import io.micrometer.tracing.Tracer
import io.tarantool.client.box.TarantoolBoxClient
import io.tarantool.core.exceptions.BoxError
import org.springframework.stereotype.Component
import ru.otus.highload.chat.config.StorageUnavailableException
import ru.otus.highload.chat.model.DialogMessage
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionException

@Component
class TarantoolDialogStorage(
    private val client: TarantoolBoxClient,
    private val tracer: Tracer,
) : DialogStorage {
    override fun send(fromUserId: UUID, toUserId: UUID, text: String, clientMessageId: UUID?): UUID {
        val span = tracer.nextSpan().name("tarantool dialog_send").start()
        try {
            tracer.withSpan(span).use {
                return call {
                    client.call(
                        "dialog_send",
                        listOf(fromUserId.toString(), toUserId.toString(), text, clientMessageId?.toString()),
                        String::class.java,
                    ).join().get()
                }.single().let(UUID::fromString)
            }
        } catch (e: Exception) {
            span.error(e)
            throw e
        } finally {
            span.end()
        }
    }

    override fun list(userId: UUID, withUserId: UUID, offset: Int, limit: Int?): List<DialogMessage> {
        val span = tracer.nextSpan().name("tarantool dialog_list").start()
        try {
            tracer.withSpan(span).use {
                return call {
                    client.call(
                        "dialog_list",
                        listOf(userId.toString(), withUserId.toString(), offset, limit),
                        object : TypeReference<List<List<Map<String, Any>>>>() {},
                    ).join().get()
                }.single().map { row ->
                    val dialogId = listOf(userId.toString(), withUserId.toString()).sorted().joinToString(":")
                    DialogMessage(
                        id = UUID.fromString(row.getValue("id").toString()),
                        dialogId = dialogId,
                        fromUserId = UUID.fromString(row.getValue("from").toString()),
                        toUserId = UUID.fromString(row.getValue("to").toString()),
                        text = row.getValue("text").toString(),
                        createdAt = instantFromNs(row.getValue("created_at_ns").toString()),
                    )
                }
            }
        } catch (e: Exception) {
            span.error(e)
            throw e
        } finally {
            span.end()
        }
    }

    private fun instantFromNs(value: String): Instant {
        val ns = value.removeSuffix("ULL").removeSuffix("LL").toLong()
        return Instant.ofEpochSecond(Math.floorDiv(ns, 1_000_000_000L), Math.floorMod(ns, 1_000_000_000L))
    }

    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (ex: CompletionException) {
        val cause = ex.cause ?: ex
        if (cause is BoxError && cause.message?.contains("BAD_REQUEST:") == true) {
            throw IllegalArgumentException(cause.message?.substringAfter("BAD_REQUEST:")?.trim(), cause)
        }
        throw StorageUnavailableException("Tarantool unavailable", cause)
    }
}
