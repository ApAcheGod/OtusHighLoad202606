package ru.otus.highload.app.config

import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

fun currentUserId(): UUID = UUID.fromString(
    SecurityContextHolder.getContext().authentication?.principal?.toString()
        ?: throw IllegalStateException("No authentication found in security context")
)
