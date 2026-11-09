package ru.otus.highload.app.config

class StorageUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
