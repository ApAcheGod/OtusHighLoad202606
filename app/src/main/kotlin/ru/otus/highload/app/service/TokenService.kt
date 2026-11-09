package ru.otus.highload.app.service

import org.springframework.stereotype.Service
import ru.otus.highload.utils.session.SessionStore
import ru.otus.highload.utils.uuidV7
import java.util.UUID

@Service
class TokenService(
    private val sessionStore: SessionStore,
) {
    fun createToken(userId: UUID): UUID {
        val token = uuidV7()
        sessionStore.create(token, userId)
        return token
    }

    fun resolveUserId(token: UUID): UUID? = sessionStore.resolveUserId(token)
}
