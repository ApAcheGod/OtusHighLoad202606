package ru.otus.highload.app.repository

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import ru.otus.highload.app.model.Gender
import ru.otus.highload.app.model.User
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

@Repository
class UserRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
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

    fun save(user: User) {
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

    fun findById(id: UUID): User? {
        val sql = """
            SELECT id, first_name, second_name, birthdate, gender, interests, biography, city, password_hash
            FROM users WHERE id = :id
        """.trimIndent()
        val params = MapSqlParameterSource("id", id)
        return jdbc.query(sql, params, rowMapper).firstOrNull()
    }

    fun count(): Long {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users",
            emptyMap<String, Any>(),
            Long::class.java)
            ?: 0
    }

    fun search(firstName: String, secondName: String): List<User> {
        val sql = """
            SELECT id, first_name, second_name, birthdate, gender, interests, biography, city, password_hash
            FROM users
            WHERE first_name ILIKE :firstName AND second_name ILIKE :secondName
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("firstName", "$firstName%")
            .addValue("secondName", "$secondName%")
        return jdbc.query(sql, params, rowMapper)
    }
}
