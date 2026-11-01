package ru.otus.highload.app.repository

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class FriendRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {

    @Transactional
    fun saveMutual(a: UUID, b: UUID) {
        val (low, high) = ordered(a, b)
        insert(low, high)
        insert(high, low)
    }

    @Transactional
    fun deleteMutual(a: UUID, b: UUID) {
        val (low, high) = ordered(a, b)
        delete(low, high)
        delete(high, low)
    }

    @Transactional(readOnly = true)
    fun findFriendIds(userId: UUID): List<UUID> {
        val sql = "SELECT friend_id FROM friends WHERE user_id = :userId"
        val params = MapSqlParameterSource("userId", userId)
        return jdbc.query(sql, params, FRIEND_ROW_MAPPER)
    }

    private fun insert(userId: UUID, friendId: UUID) {
        val sql = """
            INSERT INTO friends (user_id, friend_id)
            VALUES (:userId, :friendId)
            ON CONFLICT (user_id, friend_id) DO NOTHING
        """.trimIndent()
        jdbc.update(sql, params(userId, friendId))
    }

    private fun delete(userId: UUID, friendId: UUID) {
        val sql = """
            DELETE FROM friends
            WHERE user_id = :userId AND friend_id = :friendId
        """.trimIndent()
        jdbc.update(sql, params(userId, friendId))
    }

    private fun params(userId: UUID, friendId: UUID): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("friendId", friendId)

    private companion object {
        val FRIEND_ROW_MAPPER = RowMapper<UUID> { rs, _ -> rs.getObject("friend_id") as UUID }

        /** Детерминированный порядок для одинакового порядка блокировок на двух шардах. */
        fun ordered(a: UUID, b: UUID): Pair<UUID, UUID> =
            if (a.toString() <= b.toString()) a to b else b to a
    }
}
