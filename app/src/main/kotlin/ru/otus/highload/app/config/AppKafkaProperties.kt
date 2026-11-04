package ru.otus.highload.app.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.kafka")
class AppKafkaProperties {
    lateinit var bootstrapServers: String
    var sendTimeoutMs: Long = 10_000
}
