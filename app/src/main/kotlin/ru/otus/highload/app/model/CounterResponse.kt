package ru.otus.highload.app.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class CounterResponse(
    @JsonProperty("user_id") val userId: UUID,
    val unread: Long,
)
