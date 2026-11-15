package ru.otus.highload.user.repository

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import ru.otus.highload.user.model.Gender
import ru.otus.highload.user.model.User
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

@Repository
class JdbcUserStore(
    private val jdbc: NamedParameterJdbcTemplate,
) : UserStore {

    private val rowMapper = RowMapper<User> { rs: ResultSet, _: Int ->
        User(
            id = rs.getObject("id") as UUID,
            firstName = rs.getString("first_name"),
            secondName = rs.getString("second_name"),
            birthdate = rs.getObject("birthdate", LocalDate::class.java),
            gender = Gender.valueOf(rs.getString("gender")),
            interests = rs.getString("interests"),
            biography = rs.getString("biography"),
            city = rs.getString("city"),
            passwordHash = rs.getString("password_hash"),
        )
    }

    override fun save(user: User) {
        val sql = """
            INSERT INTO users (id, first_name, second_name, birthdate, gender, interests, biography, city, password_hash)
            VALUES (:id, :firstName, :secondName, :birthdate, :gender, :interests, :biography, :city, :passwordHash)
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("id", user.id)
            .addValue("firstName", user.firstName)
            .addValue("secondName", user.secondName)
            .addValue("birthdate", user.birthdate)
            .addValue("gender", user.gender.name)
            .addValue("interests", user.interests)
            .addValue("biography", user.biography)
            .addValue("city", user.city)
            .addValue("passwordHash", user.passwordHash)
        jdbc.update(sql, params)
    }

    override fun findById(id: UUID): User? {
        val sql = """
            SELECT id, first_name, second_name, birthdate, gender, interests, biography, city, password_hash
            FROM users WHERE id = :id
        """.trimIndent()
        return jdbc.query(sql, MapSqlParameterSource("id", id), rowMapper).firstOrNull()
    }

    override fun search(firstName: String, secondName: String, limit: Int): List<User> {
        val sql = """
            SELECT id, first_name, second_name, birthdate, gender, interests, biography, city, password_hash
            FROM users
            WHERE first_name LIKE :firstName AND second_name LIKE :secondName
            LIMIT :limit
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("firstName", "${firstName.toCapitalizedPrefix()}%")
            .addValue("secondName", "${secondName.toCapitalizedPrefix()}%")
            .addValue("limit", limit)
        return jdbc.query(sql, params, rowMapper)
    }

    override fun count(): Long =
        jdbc.queryForObject("SELECT COUNT(*) FROM users", emptyMap<String, Any>(), Long::class.java) ?: 0L

    private fun String.toCapitalizedPrefix(): String =
        lowercase().replaceFirstChar { it.uppercaseChar() }
}
