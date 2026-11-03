package ru.otus.highload.app.config

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import ru.otus.highload.utils.session.RedisSessionStore
import ru.otus.highload.utils.session.SessionStore

@Configuration
class RedisConfig {
    @Bean
    fun sessionStore(redis: StringRedisTemplate): SessionStore = RedisSessionStore(redis)

    @Bean
    fun redisHealthIndicator(redis: StringRedisTemplate): HealthIndicator = HealthIndicator {
        try {
            if (redis.connectionFactory?.connection?.ping() == "PONG") Health.up().build() else Health.down().build()
        } catch (ex: Exception) {
            Health.down(ex).build()
        }
    }
}
