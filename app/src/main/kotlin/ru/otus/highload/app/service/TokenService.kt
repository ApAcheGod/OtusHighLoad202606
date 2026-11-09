package ru.otus.highload.app.service

import com.github.benmanes.caffeine.cache.Caffeine
import io.grpc.StatusRuntimeException
import org.springframework.stereotype.Service
import ru.otus.highload.grpc.session.v1.AuthenticateRequest
import ru.otus.highload.grpc.session.v1.CountRequest
import ru.otus.highload.grpc.session.v1.CreateRequest
import ru.otus.highload.grpc.session.v1.SessionServiceGrpc
import java.time.Duration
import java.util.UUID

@Service
class TokenService(
    private val sessionStub: SessionServiceGrpc.SessionServiceBlockingStub,
) {

    private val resolveCache = Caffeine.newBuilder()
        .maximumSize(100_000)
        .expireAfterWrite(Duration.ofSeconds(5))
        .build<UUID, UUID>()

    fun createToken(userId: UUID): UUID {
        val response = sessionStub.withDeadlineAfter(RPC_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .create(CreateRequest.newBuilder().setUserId(userId.toString()).build())
        return UUID.fromString(response.token)
    }

    fun resolveUserId(token: UUID): UUID? {
        resolveCache.getIfPresent(token)?.let { return it }
        val response = sessionStub.withDeadlineAfter(RPC_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .authenticate(AuthenticateRequest.newBuilder().setToken(token.toString()).build())
        val userId = response.userId.takeIf { it.isNotEmpty() }?.let(UUID::fromString)
        userId?.let { resolveCache.put(token, it) }
        return userId
    }

    fun countSessions(): Long = try {
        sessionStub.withDeadlineAfter(RPC_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .count(CountRequest.getDefaultInstance()).count
    } catch (ex: StatusRuntimeException) {
        0L
    }

    companion object {
        private val RPC_TIMEOUT = Duration.ofMillis(500)
    }
}
