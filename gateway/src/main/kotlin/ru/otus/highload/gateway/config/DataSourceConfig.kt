package ru.otus.highload.gateway.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class DataSourceConfig(
    @Value("\${app.datasource.url}") private val dbUrl: String,
    @Value("\${app.datasource.username}") private val dbUsername: String,
    @Value("\${app.datasource.password}") private val dbPassword: String,
    @Value("\${app.datasource.pool-size:5}") private val poolSize: Int,
    @Value("\${app.datasource.connection-timeout:5000}") private val connectTimeoutMillis: Long,
) {
    @Bean
    fun dataSource(): DataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = dbUrl
                username = dbUsername
                password = dbPassword
                poolName = "gateway-sessions"
                maximumPoolSize = poolSize
                this.connectionTimeout = connectTimeoutMillis
                isReadOnly = true
                dataSourceProperties["stringtype"] = "unspecified"
            }
        )
}
