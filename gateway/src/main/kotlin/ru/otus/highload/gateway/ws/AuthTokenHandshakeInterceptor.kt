package ru.otus.highload.gateway.ws

import org.slf4j.LoggerFactory
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import ru.otus.highload.gateway.auth.SessionLookup
import java.util.UUID


@Component
class AuthTokenHandshakeInterceptor(
    private val sessionLookup: SessionLookup,
) : HandshakeInterceptor {
    private val log = LoggerFactory.getLogger(AuthTokenHandshakeInterceptor::class.java)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val token = extractToken(request) ?: run {
            reject(response, "missing token")
            return false
        }
        val userId = try {
            sessionLookup.findUserIdByToken(token)
        } catch (ex: RedisConnectionFailureException) {
            reject(response, "session storage unavailable", HttpStatus.SERVICE_UNAVAILABLE)
            return false
        } ?: run {
            reject(response, "invalid token")
            return false
        }
        attributes[USER_ID_ATTR] = userId
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) = Unit

    private fun extractToken(request: ServerHttpRequest): UUID? {
        val header = request.headers.getFirst("Authorization")
        val raw = when {
            header != null && header.startsWith("Bearer ") -> header.removePrefix("Bearer ")
            request is ServletServerHttpRequest -> request.servletRequest.getParameter("token")
            else -> null
        } ?: return null
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun reject(
        response: ServerHttpResponse,
        reason: String,
        status: HttpStatus = HttpStatus.UNAUTHORIZED,
    ) {
        log.debug("WS handshake rejected: {}", reason)
        response.setStatusCode(status)
    }

    companion object {
        const val USER_ID_ATTR = "userId"
    }
}
