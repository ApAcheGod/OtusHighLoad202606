package ru.otus.highload.app.liquibase

import liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import org.slf4j.LoggerFactory
import ru.otus.highload.utils.uuidV7
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * Тестовые данные для ленты:
 * - дружественный граф для первых N пользователей (в обе стороны);
 * - посты из posts.txt, размноженные по «активным» авторам (у каждого > 1000 постов,
 *   чтобы проверять ограничение ленты в 1000 записей).
 */
class PostsSeedChange : CustomTaskChange {

    private var fileOpener: ResourceAccessor? = null
    private var postsPath: String = "db/changelog/data/posts.txt"
    private var userCount: Int = 1000
    private var powerUsers: Int = 60
    private var cycles: Int = 20

    fun setPostsPath(postsPath: String) {
        this.postsPath = postsPath
    }

    // Liquibase вызывает сеттеры через рефлексию со значениями типа String
    fun setUserCount(userCount: String) {
        this.userCount = userCount.toInt()
    }

    fun setPowerUsers(powerUsers: String) {
        this.powerUsers = powerUsers.toInt()
    }

    fun setCycles(cycles: String) {
        this.cycles = cycles.toInt()
    }

    override fun setFileOpener(fileOpener: ResourceAccessor) {
        this.fileOpener = fileOpener
    }

    override fun setUp() = Unit

    override fun validate(database: Database): ValidationErrors = ValidationErrors()

    override fun getConfirmationMessage(): String =
        "Seeded friends graph and posts from $postsPath"

    override fun execute(database: Database) {
        val connection = (database.connection as JdbcConnection).wrappedConnection
        val users = loadUsers(connection)
        if (users.isEmpty()) {
            log.warn("No users found, skipping posts seed")
            return
        }

        val friendsInserted = seedFriends(connection, users)
        val postsInserted = seedPosts(connection, users)
        log.info("Seeded {} friendship rows, {} posts", friendsInserted, postsInserted)
    }

    private fun loadUsers(connection: java.sql.Connection): List<String> {
        connection.prepareStatement("SELECT id FROM users ORDER BY id LIMIT ?").use { st ->
            st.setInt(1, userCount)
            st.executeQuery().use { rs: ResultSet ->
                val ids = ArrayList<String>(userCount)
                while (rs.next()) ids.add(rs.getString(1))
                return ids
            }
        }
    }

    private fun seedFriends(connection: java.sql.Connection, users: List<String>): Int {
        val pairs = LinkedHashSet<Pair<String, String>>()
        for ((i, user) in users.withIndex()) {
            // близкие соседи
            for (d in 1..3) {
                val other = users[(i + d) % users.size]
                if (other != user) pairs.add(user to other)
            }
            // каждый видит активных авторов
            val power = users[i % minOf(powerUsers, users.size)]
            if (power != user) pairs.add(user to power)
        }
        // обратные рёбра, чтобы авторы видели своих подписчиков и наоборот
        val all = LinkedHashSet<Pair<String, String>>()
        for ((a, b) in pairs) {
            all.add(a to b)
            all.add(b to a)
        }

        connection.prepareStatement(
            "INSERT INTO friends (user_id, friend_id) VALUES (?::uuid, ?::uuid) ON CONFLICT DO NOTHING"
        ).use { st ->
            var count = 0
            for ((a, b) in all) {
                st.setString(1, a)
                st.setString(2, b)
                st.addBatch()
                count++
                if (count % BATCH == 0) st.executeBatch()
            }
            st.executeBatch()
            return count
        }
    }

    private fun seedPosts(connection: java.sql.Connection, users: List<String>): Int {
        val stream = requireNotNull(fileOpener?.openStream(null, postsPath)) {
            "Resource not found: $postsPath"
        }
        val texts = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
            .use { it.lineSequence().filter { line -> line.isNotBlank() }.toList() }
        if (texts.isEmpty()) return 0

        val authors = users.take(minOf(powerUsers, users.size))
        val now = Instant.now().epochSecond
        val horizonSeconds = 90L * 24 * 3600

        connection.prepareStatement(
            "INSERT INTO posts (id, user_id, text, created_at, likes) VALUES (?::uuid, ?::uuid, ?, ?, 0)"
        ).use { st ->
            var count = 0
            var i = 0
            repeat(cycles) { cycle ->
                for (text in texts) {
                    val author = authors[(i + cycle) % authors.size]
                    val age = (i * 37L + cycle * 17L) % horizonSeconds
                    st.setString(1, uuidV7().toString())
                    st.setString(2, author)
                    st.setString(3, text)
                    st.setTimestamp(4, Timestamp.from(Instant.ofEpochSecond(now - age)))
                    st.addBatch()
                    count++
                    if (count % BATCH == 0) st.executeBatch()
                    i++
                }
            }
            st.executeBatch()
            return count
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(PostsSeedChange::class.java)
        const val BATCH = 1000
    }
}
