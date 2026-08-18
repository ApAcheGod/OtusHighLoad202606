package ru.otus.highload.app.liquibase

import liquibase.change.custom.CustomTaskChange
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import org.postgresql.core.BaseConnection
import org.postgresql.copy.CopyManager
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class PeopleDataCopyChange : CustomTaskChange {

    private var fileOpener: ResourceAccessor? = null
    private var csvPath: String = "db/changelog/data/people.v2.csv"

    fun setCsvPath(csvPath: String) {
        this.csvPath = csvPath
    }

    override fun setFileOpener(fileOpener: ResourceAccessor) {
        this.fileOpener = fileOpener
    }

    override fun setUp() = Unit

    override fun validate(database: Database): ValidationErrors = ValidationErrors()

    override fun getConfirmationMessage(): String = "People loaded from $csvPath via COPY"

    override fun execute(database: Database) {
        val baseConnection = (database.connection as JdbcConnection)
            .wrappedConnection
            .unwrap(BaseConnection::class.java)
        val stream = requireNotNull(fileOpener?.openStream(null, csvPath)) {
            "Resource not found: $csvPath"
        }
        stream.use {
            val loader = PeopleCopyLoader(BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)))
            val loaded = CopyManager(baseConnection).copyIn(COPY_SQL, loader.reader)
            log.info("COPY loaded {} rows, skipped {}", loaded, loader.skippedRows)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(PeopleDataCopyChange::class.java)

        val COLUMNS = "id, first_name, second_name, birthdate, gender, interests, biography, city, password_hash"

        val COPY_SQL = """
            COPY users ($COLUMNS)
            FROM STDIN WITH (FORMAT csv)
        """.trimIndent()
    }
}
