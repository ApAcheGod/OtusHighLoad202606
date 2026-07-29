package ru.otus.highload.app.repository

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class SessionRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun save(token: UUID, userId: UUID) {
        val sql = "INSERT INTO sessions (token, user_id) VALUES (:token, :userId)"
        val params = MapSqlParameterSource()
            .addValue("token", token)
            .addValue("userId", userId)
        jdbc.update(sql, params)
    }

    fun count(): Long {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sessions", emptyMap<String, Any>(), Long::class.java) ?: 0
    }

    fun findUserIdByToken(token: UUID): UUID? {
        val sql = "SELECT user_id FROM sessions WHERE token = :token"
        val params = MapSqlParameterSource("token", token)
        return jdbc.query(sql, params, org.springframework.jdbc.core.ResultSetExtractor { rs ->
            if (rs.next()) rs.getObject("user_id") as UUID else null
        })
    }
}
