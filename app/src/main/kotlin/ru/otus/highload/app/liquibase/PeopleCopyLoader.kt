package ru.otus.highload.app.liquibase

import ru.otus.highload.utils.uuidV7
import java.io.BufferedReader
import java.io.Reader
import java.util.UUID

class PeopleCopyLoader(source: BufferedReader) {

    private val seen = HashSet<Person>()

    var skippedRows: Long = 0
        private set

    val reader: Reader = RowSequenceReader(source.lineSequence().mapNotNull { toCopyRow(it) }.iterator())

    private fun toCopyRow(line: String): String? {
        val person = Person.parse(line) ?: run {
            skippedRows++
            return null
        }
        if (!seen.add(person)) {
            skippedRows++
            return null
        }
        val id = uuidV7()
        val gender = if (id.leastSignificantBits and 1L == 0L) "MALE" else "FEMALE"
        return person.toCopyRow(id, gender)
    }
}
