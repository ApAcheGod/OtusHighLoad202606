package ru.otus.highload.app.config

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Configuration
import ru.otus.highload.app.repository.UserRepository
import ru.otus.highload.utils.session.SessionStore

@Configuration
class MetricsConfig(
    registry: MeterRegistry,
    userRepository: UserRepository,
    sessionStore: SessionStore,
) {
    init {
        Gauge.builder("app_users_total", userRepository) { it.count().toDouble() }
            .description("Total number of registered users")
            .register(registry)

        Gauge.builder("app_sessions_active", sessionStore) { it.count().toDouble() }
            .description("Number of active sessions")
            .register(registry)
    }
}
