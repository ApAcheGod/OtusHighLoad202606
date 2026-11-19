package ru.otus.highload.counter.model

import com.fasterxml.jackson.annotation.JsonProperty

data class CounterSnapshot(
    @JsonProperty("user_id") val userId: String,
    val count: Long,
    val messages: List<CounterMessageState>,
)

data class CounterMessageState(
    val id: String,
    val state: String,
    val version: Long,
)
