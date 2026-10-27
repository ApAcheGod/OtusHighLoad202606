package ru.otus.highload.app.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class DialogSendRequest(
    val text: String,
    @JsonProperty("client_message_id")
    val clientMessageId: UUID? = null,
)
