package ru.otus.highload.app.repository

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import ru.otus.highload.app.model.Post
import ru.otus.highload.app.service.FeedItemData
import java.util.UUID

@Repository
class PostRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {

    @Transactional
    fun save(post: Post) {
        val sql = """
            INSERT INTO posts (id, user_id, text, created_at, likes)
            VALUES (:id, :userId, :text, :createdAt, :likes)
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("id", post.id)
            .addValue("userId", post.userId)
            .addValue("text", post.text)
            .addValue("createdAt", post.createdAt)
            .addValue("likes", post.likes)
        jdbc.update(sql, params)
    }

    /** Чтение по id: scatter по шардам — distribution-колонка user_id неизвестна. */
    @Transactional
    fun findById(id: UUID): Post? {
        val sql = """
            SELECT id, user_id, text, created_at, likes
            FROM posts WHERE id = :id
        """.trimIndent()
        val params = MapSqlParameterSource("id", id)
        return jdbc.query(sql, params, PostRowMapper).firstOrNull()
    }

    @Transactional
    fun update(id: UUID, userId: UUID, newText: String): Post? {
        val sql = """
            UPDATE posts
            SET text = :text
            WHERE user_id = :userId AND id = :id
            RETURNING id, user_id, text, created_at, likes
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("id", id)
            .addValue("userId", userId)
            .addValue("text", newText)
        return jdbc.query(sql, params, PostRowMapper).firstOrNull()
    }

    @Transactional
    fun deleteById(id: UUID, userId: UUID): Boolean {
        val sql = "DELETE FROM posts WHERE user_id = :userId AND id = :id"
        val params = MapSqlParameterSource()
            .addValue("id", id)
            .addValue("userId", userId)
        return jdbc.update(sql, params) > 0
    }

    /** Гидратация ленты по id — scatter по шардам (см. findById). */
    @Transactional
    fun findByIds(ids: List<UUID>): List<Post> {
        if (ids.isEmpty()) return emptyList()

        val placeholders = ids.indices.joinToString(", ") { ":id$it" }
        val sql = """
            SELECT id, user_id, text, created_at, likes
            FROM posts WHERE id IN ($placeholders)
        """.trimIndent()

        val params = MapSqlParameterSource().apply {
            ids.forEachIndexed { index, id ->
                addValue("id$index", id)
            }
        }

        return jdbc.query(sql, params, PostRowMapper)
    }

    /** Последние посты автора (бэкфилл ленты при добавлении друга) — single-shard по user_id. */
    @Transactional
    fun findLatestByAuthor(authorId: UUID, limit: Int): List<FeedItemData> {
        val sql = """
            SELECT id, user_id,
                   EXTRACT(EPOCH FROM created_at) * 1000 AS score_ms
            FROM posts
            WHERE user_id = :authorId
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("authorId", authorId)
            .addValue("limit", limit)
        return jdbc.query(sql, params, FeedCandidateRowMapper)
    }

    /** Последние посты друзей (прогрев ленты) — join friends+posts в одной colocation-группе. */
    @Transactional
    fun findFriendFeedCandidates(userId: UUID, limit: Int): List<FeedItemData> {
        val sql = """
            SELECT p.id, p.user_id,
                   EXTRACT(EPOCH FROM p.created_at) * 1000 AS score_ms
            FROM posts p
            WHERE p.user_id IN (SELECT friend_id FROM friends WHERE user_id = :userId)
            ORDER BY p.created_at DESC, p.id DESC
            LIMIT :limit
        """.trimIndent()
        val params = MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("limit", limit)
        return jdbc.query(sql, params, FeedCandidateRowMapper)
    }
}

object PostRowMapper : RowMapper<Post> {
    override fun mapRow(rs: java.sql.ResultSet, rowNum: Int): Post {
        return Post(
            id = UUID.fromString(rs.getString("id")),
            userId = UUID.fromString(rs.getString("user_id")),
            text = rs.getString("text"),
            createdAt = rs.getString("created_at"),
            likes = rs.getInt("likes")
        )
    }
}

object FeedCandidateRowMapper : RowMapper<FeedItemData> {
    override fun mapRow(rs: java.sql.ResultSet, rowNum: Int): FeedItemData {
        return FeedItemData(
            postId = UUID.fromString(rs.getString("id")),
            authorId = UUID.fromString(rs.getString("user_id")),
            score = rs.getLong("score_ms"),
        )
    }
}
