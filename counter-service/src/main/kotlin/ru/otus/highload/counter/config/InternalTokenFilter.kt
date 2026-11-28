package ru.otus.highload.counter.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Component
class InternalTokenFilter(
    @Value("\${app.internal-token}") private val internalToken: String,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/actuator/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = request.getHeader("X-Internal-Token")
        val valid = token != null && MessageDigest.isEqual(
            token.toByteArray(StandardCharsets.UTF_8),
            internalToken.toByteArray(StandardCharsets.UTF_8),
        )
        if (!valid) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid internal token")
            return
        }
        filterChain.doFilter(request, response)
    }
}
