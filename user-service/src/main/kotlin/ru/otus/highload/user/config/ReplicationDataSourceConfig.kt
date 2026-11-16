package ru.otus.highload.user.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import javax.sql.DataSource

@Configuration
class ReplicationDataSourceConfig(
    @Value("\${app.datasource.master.url}") private val masterUrl: String,
    @Value("\${app.datasource.master.username}") private val masterUsername: String,
    @Value("\${app.datasource.master.password}") private val masterPassword: String,
    @Value("\${app.datasource.slave.url}") private val slaveUrl: String,
    @Value("\${app.datasource.slave.username}") private val slaveUsername: String,
    @Value("\${app.datasource.slave.password}") private val slavePassword: String,
    @Value("\${app.datasource.pool-size:25}") private val poolSize: Int,
    @Value("\${app.datasource.connection-timeout:5000}") private val connectionTimeout: Long,
) {

    @Bean
    fun masterDataSource(): DataSource = hikari(masterUrl, masterUsername, masterPassword, "master")

    @Bean
    fun slaveDataSource(): DataSource = hikari(slaveUrl, slaveUsername, slavePassword, "slave")

    @Bean
    fun routingDataSource(
        @Qualifier("masterDataSource") masterDataSource: DataSource,
        @Qualifier("slaveDataSource") slaveDataSource: DataSource,
    ): DataSource {
        val routing = ReplicationRoutingDataSource(
            master = masterDataSource,
            slaves = listOf(slaveDataSource),
        )
        routing.afterPropertiesSet()
        return routing
    }

    @Bean
    @Primary
    fun clientDataSource(@Qualifier("routingDataSource") routingDataSource: DataSource): DataSource =
        LazyConnectionDataSourceProxy(routingDataSource)

    @Bean
    @Primary
    fun writeJdbcTemplate(@Qualifier("clientDataSource") dataSource: DataSource): NamedParameterJdbcTemplate =
        NamedParameterJdbcTemplate(dataSource)

    @Bean
    fun readOnlyJdbcTemplate(@Qualifier("slaveDataSource") dataSource: DataSource): NamedParameterJdbcTemplate =
        NamedParameterJdbcTemplate(dataSource)

    private fun hikari(url: String, username: String, password: String, poolName: String): DataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = url
                this.username = username
                this.password = password
                this.poolName = poolName
                maximumPoolSize = poolSize
                connectionTimeout = this@ReplicationDataSourceConfig.connectionTimeout
                maxLifetime = 25_000
                dataSourceProperties["preparedStatementCacheQueries"] = "512"
                dataSourceProperties["preparedStatementCacheSizeMiB"] = "10"
                dataSourceProperties["stringtype"] = "unspecified"
            }
        )
}
