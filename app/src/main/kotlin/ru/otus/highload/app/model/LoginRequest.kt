package ru.otus.highload.app.model

import java.util.UUID

data class LoginRequest(
    val id: UUID,
    val password: String,
)
