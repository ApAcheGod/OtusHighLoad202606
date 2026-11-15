package ru.otus.highload.app.config

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Configuration
import ru.otus.highload.app.service.TokenService
import ru.otus.highload.app.service.UserService

@Configuration
class MetricsConfig(
    registry: MeterRegistry,
    userService: UserService,
    tokenService: TokenService,
) {
    init {
        Gauge.builder("app_users_total", userService) { it.countUsers().toDouble() }
            .description("Total number of registered users (via user-service)")
            .register(registry)

        Gauge.builder("app_sessions_active", tokenService) { it.countSessions().toDouble() }
            .description("Number of active sessions (via auth-service)")
            .register(registry)
    }
}
