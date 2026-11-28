package ru.otus.highload.counter.projection

enum class ApplyResult {
    APPLIED,
    DUPLICATE,
    STALE,
    INVARIANT_VIOLATION,
}
