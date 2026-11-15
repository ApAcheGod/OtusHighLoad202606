package ru.otus.highload.app.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class ChatClientConfig {

    @Bean
    fun chatServiceRestClient(
        builder: RestClient.Builder,
        @Value("\${app.chat-service.url:http://localhost:8083}") baseUrl: String,
    ): RestClient = builder.baseUrl(baseUrl).build()
}
