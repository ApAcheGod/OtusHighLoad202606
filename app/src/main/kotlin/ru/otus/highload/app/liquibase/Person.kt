package ru.otus.highload.app.liquibase

import java.util.UUID

data class Person(
    val secondName: String,
    val firstName: String,
    val birthdate: String,
    val city: String,
) {
    fun toCopyRow(id: UUID, gender: String): String =
        "$id,$firstName,$secondName,$birthdate,$gender,,,$city,$DUMMY_PASSWORD_HASH"

    companion object {
        fun parse(line: String): Person? {
            val parts = line.split(',')
            if (parts.size != 3) return null
            val nameParts = parts[0].split(' ', limit = 2)
            if (nameParts.size != 2) return null
            val secondName = nameParts[0]
            val firstName = nameParts[1]
            val birthdate = parts[1]
            val city = parts[2]
            if (secondName.isEmpty() || firstName.isEmpty() || birthdate.isEmpty() || city.isEmpty()) return null
            return Person(secondName, firstName, birthdate, city)
        }

        val DUMMY_PASSWORD_HASH = "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    }
}
