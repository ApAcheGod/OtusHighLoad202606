package ru.otus.highload.app.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class PostResponse(
    val id: UUID,
    val text: String,
    @JsonProperty("author_user_id")
    val authorUserId: UUID,
)
