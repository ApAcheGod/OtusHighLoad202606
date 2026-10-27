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
    fun save(userId: UUID, friendId: UUID) {
        val sql = """
            INSERT INTO friends (user_id, friend_id)
            VALUES (:userId, :friendId)
            ON CONFLICT (user_id, friend_id) DO NOTHING
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("friendId", friendId)
        jdbc.update(sql, params)
    }

    @Transactional
    fun deleteByUserIdAndFriendId(userId: UUID, friendId: UUID) {
        val sql = """
            DELETE FROM friends
            WHERE user_id = :userId AND friend_id = :friendId
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("friendId", friendId)
        jdbc.update(sql, params)
    }

    /** Кто добавил пользователя (кому фан-аутить его новые посты). */
    @Transactional(readOnly = true)
    fun findFollowerIds(userId: UUID): List<UUID> {
        val sql = "SELECT user_id FROM friends WHERE friend_id = :userId"
        val params = MapSqlParameterSource("userId", userId)
        return jdbc.query(sql, params, FOLLOWER_ROW_MAPPER)
    }

    private companion object {
        val FOLLOWER_ROW_MAPPER = RowMapper<UUID> { rs, _ -> rs.getObject("user_id") as UUID }
    }
}
