package ru.otus.highload.app.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.otus.highload.app.repository.SessionRepository
import ru.otus.highload.utils.uuidV7
import java.util.UUID

@Service
class TokenService(
    private val sessionRepository: SessionRepository,
) {
    @Transactional
    fun createToken(userId: UUID): UUID {
        val token = uuidV7()
        sessionRepository.save(token, userId)
        return token
    }

    @Transactional(readOnly = true)
    fun resolveUserId(token: UUID): UUID? {
        return sessionRepository.findUserIdByToken(token)
    }
}
