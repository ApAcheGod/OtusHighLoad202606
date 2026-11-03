package ru.otus.highload.gateway.auth

import org.springframework.stereotype.Component
import ru.otus.highload.utils.session.SessionStore
import java.util.UUID

@Component
class SessionLookup(
    private val sessionStore: SessionStore,
) {
    fun findUserIdByToken(token: UUID): UUID? = sessionStore.resolveUserId(token)
}
