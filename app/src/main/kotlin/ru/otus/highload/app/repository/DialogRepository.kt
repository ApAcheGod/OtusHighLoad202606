package ru.otus.highload.app.repository

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import ru.otus.highload.app.model.DialogMessage
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class DialogRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {

    @Transactional
    fun save(message: DialogMessage, clientMessageId: UUID? = null): Boolean {
        val sql = """
            INSERT INTO dialog_messages
                (pair_key, user_low_id, user_high_id, id, from_user_id, text, created_at, client_message_id)
            VALUES
                (:pairKey, :userLowId, :userHighId, :id, :fromUserId, :text, :createdAt, :clientMessageId)
            ON CONFLICT (pair_key, client_message_id) DO NOTHING
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("pairKey", pairKey(message.userLowId, message.userHighId))
            .addValue("userLowId", message.userLowId)
            .addValue("userHighId", message.userHighId)
            .addValue("id", message.id)
            .addValue("fromUserId", message.fromUserId)
            .addValue("text", message.text)
            .addValue("createdAt", message.createdAt.atOffset(ZoneOffset.UTC))
            .addValue("clientMessageId", clientMessageId)
        return jdbc.update(sql, params) > 0
    }

    @Transactional(readOnly = true)
    fun findIdByClientMessageId(pairKey: String, clientMessageId: UUID): UUID? {
        val sql = """
            SELECT id FROM dialog_messages
            WHERE pair_key = :pairKey AND client_message_id = :clientMessageId
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("pairKey", pairKey)
            .addValue("clientMessageId", clientMessageId)
        return jdbc.query(sql, params) { rs, _ -> rs.getObject("id", UUID::class.java) }.firstOrNull()
    }

    @Transactional(readOnly = true)
    fun findConversation(userLowId: UUID, userHighId: UUID, offset: Int, limit: Int?): List<DialogMessage> {
        val sql = buildString {
            append(
                """
                SELECT user_low_id, user_high_id, id, from_user_id, text, created_at
                FROM dialog_messages
                WHERE pair_key = :pairKey
                ORDER BY created_at ASC, id ASC
                """.trimIndent()
            )
            when {
                limit != null -> append("\nOFFSET :offset LIMIT :limit")
                offset > 0 -> append("\nOFFSET :offset")
            }
        }
        val params = MapSqlParameterSource()
            .addValue("pairKey", pairKey(userLowId, userHighId))
            .addValue("offset", offset)
            .addValue("limit", limit)
        return jdbc.query(sql, params, DialogMessageRowMapper)
    }

    companion object {
        fun pairKey(userLowId: UUID, userHighId: UUID): String = "$userLowId:$userHighId"
    }

    private object DialogMessageRowMapper : RowMapper<DialogMessage> {
        override fun mapRow(rs: ResultSet, rowNum: Int): DialogMessage = DialogMessage(
            id = UUID.fromString(rs.getString("id")),
            userLowId = UUID.fromString(rs.getString("user_low_id")),
            userHighId = UUID.fromString(rs.getString("user_high_id")),
            fromUserId = UUID.fromString(rs.getString("from_user_id")),
            text = rs.getString("text"),
            createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
        )
    }
}
