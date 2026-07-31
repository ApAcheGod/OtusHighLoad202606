package ru.otus.highload.app.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

data class RegisterRequest(
    @field:JsonProperty("first_name") val firstName: String,
    @field:JsonProperty("second_name") val secondName: String,
    val birthdate: LocalDate,
    val gender: Gender,
    val interests: String?,
    val biography: String?,
    val city: String?,
    val password: String,
)
