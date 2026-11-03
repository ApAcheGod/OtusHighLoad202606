package ru.otus.highload.gateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import ru.otus.highload.utils.session.RedisSessionStore
import ru.otus.highload.utils.session.SessionStore

@Configuration
class RedisConfig {
    @Bean
    fun sessionStore(redis: StringRedisTemplate): SessionStore = RedisSessionStore(redis)
}
