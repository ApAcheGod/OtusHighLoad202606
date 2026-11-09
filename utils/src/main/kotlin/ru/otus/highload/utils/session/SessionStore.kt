package ru.otus.highload.utils.session

import java.util.UUID

interface SessionStore {
    fun create(token: UUID, userId: UUID)
    fun resolveUserId(token: UUID): UUID?
    fun count(): Long
}
