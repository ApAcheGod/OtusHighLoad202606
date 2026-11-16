package ru.otus.highload.app.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.observation.SecurityObservationSettings

@Configuration
class SecurityObservationConfig {
    @Bean
    fun securityObservationSettings(): SecurityObservationSettings = SecurityObservationSettings.noObservations()
}
