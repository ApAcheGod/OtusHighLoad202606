package ru.otus.highload.gateway.auth

import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class SessionLookup(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun findUserIdByToken(token: UUID): UUID? {
        val sql = "SELECT user_id FROM sessions WHERE token = :token"
        return jdbc.query(sql, MapSqlParameterSource("token", token), ResultSetExtractor { rs ->
            if (rs.next()) rs.getObject("user_id") as UUID else null
        })
    }
}
