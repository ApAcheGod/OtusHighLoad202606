package ru.otus.highload.app.model

import java.time.LocalDate
import java.util.UUID

data class User(
    val id: UUID,
    val firstName: String,
    val secondName: String,
    val birthdate: LocalDate,
    val gender: Gender,
    val interests: String?,
    val biography: String?,
    val city: String?,
    val passwordHash: String,
)

enum class Gender {
    MALE, FEMALE
}
