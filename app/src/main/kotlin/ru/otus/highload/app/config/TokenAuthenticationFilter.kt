package ru.otus.highload.app.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import ru.otus.highload.app.service.TokenService
import java.util.UUID

@Component
class TokenAuthenticationFilter(
    private val tokenService: TokenService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val tokenStr = authHeader.removePrefix("Bearer ")
            val token = try {
                UUID.fromString(tokenStr)
            } catch (_: IllegalArgumentException) {
                null
            }

            if (token != null) {
                val userId = tokenService.resolveUserId(token)
                if (userId != null) {
                    val auth = UsernamePasswordAuthenticationToken(
                        userId, null, emptyList()
                    )
                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}
