package ru.otus.highload.friend.repository

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
        val sql = """
            INSERT INTO friends (user_id, friend_id)
            VALUES (:firstUserId, :firstFriendId), (:secondUserId, :secondFriendId)
            ON CONFLICT (user_id, friend_id) DO NOTHING
        """.trimIndent()
        jdbc.update(sql, mutualParams(low, high))
    }

    @Transactional
    fun deleteMutual(a: UUID, b: UUID) {
        val (low, high) = ordered(a, b)
        val sql = """
            DELETE FROM friends
            WHERE (user_id, friend_id) IN
                  ((:firstUserId, :firstFriendId), (:secondUserId, :secondFriendId))
        """.trimIndent()
        jdbc.update(sql, mutualParams(low, high))
    }

    @Transactional(readOnly = true)
    fun findFriendIds(userId: UUID): List<UUID> {
        val sql = "SELECT friend_id FROM friends WHERE user_id = :userId"
        val params = MapSqlParameterSource("userId", userId)
        return jdbc.query(sql, params, FRIEND_ROW_MAPPER)
    }

    private fun mutualParams(low: UUID, high: UUID): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("firstUserId", low)
            .addValue("firstFriendId", high)
            .addValue("secondUserId", high)
            .addValue("secondFriendId", low)

    private companion object {
        val FRIEND_ROW_MAPPER = RowMapper<UUID> { rs, _ -> rs.getObject("friend_id") as UUID }

        fun ordered(a: UUID, b: UUID): Pair<UUID, UUID> =
            if (a.toString() <= b.toString()) a to b else b to a
    }
}
