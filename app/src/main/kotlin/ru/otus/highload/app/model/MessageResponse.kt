package ru.otus.highload.app.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID


data class MessageResponse(
    val id: UUID,
    @JsonProperty("from")
    val fromUserId: UUID,
    @JsonProperty("to")
    val toUserId: UUID,
    val text: String,
    @JsonProperty("created_at")
    val createdAt: String,
)
