package ru.otus.highload.app.config

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.springframework.boot.http.client.HttpComponentsClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ApacheHttpClientPoolConfig {
    @Bean
    fun apacheHttpClientPoolCustomizer():
        ClientHttpRequestFactoryBuilderCustomizer<HttpComponentsClientHttpRequestFactoryBuilder> =
        ClientHttpRequestFactoryBuilderCustomizer { builder ->
            builder.withConnectionManagerCustomizer { pool: PoolingHttpClientConnectionManagerBuilder ->
                pool.setMaxConnTotal(120).setMaxConnPerRoute(120)
            }
        }
}
