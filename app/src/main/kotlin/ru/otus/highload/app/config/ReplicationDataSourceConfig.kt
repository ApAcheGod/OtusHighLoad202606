package ru.otus.highload.app.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import javax.sql.DataSource

@Configuration
class ReplicationDataSourceConfig(
    @Value("\${app.datasource.master.url}") private val masterUrl: String,
    @Value("\${app.datasource.master.username}") private val masterUsername: String,
    @Value("\${app.datasource.master.password}") private val masterPassword: String,
    @Value("\${app.datasource.slave.urls}") private val slaveUrlsRaw: String,
    @Value("\${app.datasource.slave.username}") private val slaveUsername: String,
    @Value("\${app.datasource.slave.password}") private val slavePassword: String,
    @Value("\${app.datasource.pool-size:25}") private val poolSize: Int,
    @Value("\${app.datasource.slave.pool-size:25}") private val slavePoolSize: Int,
    @Value("\${app.datasource.connection-timeout:5000}") private val connectionTimeout: Long,
) {
    @Bean
    fun masterDataSource(): DataSource = hikari(masterUrl, masterUsername, masterPassword, "master")

    @Bean
    fun slaveDataSource1(): DataSource = slave(0)

    @Bean
    fun slaveDataSource2(): DataSource = slave(1)

    @Bean
    fun routingDataSource(
        @Qualifier("masterDataSource") masterDataSource: DataSource,
        @Qualifier("slaveDataSource1") slaveDataSource1: DataSource,
        @Qualifier("slaveDataSource2") slaveDataSource2: DataSource,
    ): DataSource {
        val routing = ReplicationRoutingDataSource(
            master = masterDataSource,
            slaves = listOf(slaveDataSource1, slaveDataSource2),
        )
        routing.afterPropertiesSet()
        return routing
    }

    @Bean
    @Primary
    fun clientDataSource(@Qualifier("routingDataSource") routingDataSource: DataSource): DataSource =
        LazyConnectionDataSourceProxy(routingDataSource)

    private fun slave(index: Int): DataSource =
        hikari(slaveUrls[if (index < slaveUrls.size) index else slaveUrls.lastIndex], slaveUsername, slavePassword, "slave-${index + 1}", slavePoolSize)

    private val slaveUrls: List<String>
        get() = slaveUrlsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun hikari(url: String, username: String, password: String, poolName: String, poolSize: Int = this.poolSize): DataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = url
                this.username = username
                this.password = password
                this.poolName = poolName
                maximumPoolSize = poolSize
                connectionTimeout = this@ReplicationDataSourceConfig.connectionTimeout
                dataSourceProperties["preparedStatementCacheQueries"] = "512"
                dataSourceProperties["preparedStatementCacheSizeMiB"] = "10"
                dataSourceProperties["stringtype"] = "unspecified"
            }
        )
}