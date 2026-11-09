package ru.otus.highload.chat.config

import io.tarantool.client.box.TarantoolBoxClient
import io.tarantool.client.factory.TarantoolFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TarantoolConfig {
    @Bean(destroyMethod = "close")
    fun tarantoolClient(
        @Value("\${app.tarantool.host}") host: String,
        @Value("\${app.tarantool.port:3301}") port: Int,
        @Value("\${app.tarantool.username}") username: String,
        @Value("\${app.tarantool.password}") password: String,
    ): TarantoolBoxClient = TarantoolFactory.box()
        .withHost(host)
        .withPort(port)
        .withUser(username)
        .withPassword(password)
        .build()
}
