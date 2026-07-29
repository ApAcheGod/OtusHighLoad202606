package ru.otus.highload.app.config

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Configuration
import ru.otus.highload.app.repository.SessionRepository
import ru.otus.highload.app.repository.UserRepository

@Configuration
class MetricsConfig(
    registry: MeterRegistry,
    userRepository: UserRepository,
    sessionRepository: SessionRepository,
) {
    init {
        Gauge.builder("app_users_total", userRepository) { it.count().toDouble() }
            .description("Total number of registered users")
            .register(registry)

        Gauge.builder("app_sessions_active", sessionRepository) { it.count().toDouble() }
            .description("Number of active sessions")
            .register(registry)
    }
}
