package ru.otus.highload.chat.model

import com.fasterxml.jackson.annotation.JsonProperty

data class DialogSnapshot(
    @JsonProperty("user_id") val userId: String,
    val count: Long,
    val messages: List<DialogMessageState>,
)

data class DialogMessageState(
    val id: String,
    val state: String,
    val version: Long,
)
