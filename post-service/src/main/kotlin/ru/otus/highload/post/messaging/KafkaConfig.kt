package ru.otus.highload.post.messaging

import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate

@Configuration
@EnableConfigurationProperties(PostKafkaProperties::class)
class KafkaConfig {

    @Bean
    fun kafkaAdmin(props: PostKafkaProperties): KafkaAdmin =
        KafkaAdmin(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to props.bootstrapServers))

    @Bean
    fun producerFactory(props: PostKafkaProperties): DefaultKafkaProducerFactory<String, String> =
        DefaultKafkaProducerFactory(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to props.bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.ACKS_CONFIG to "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
            )
        )

    @Bean
    fun kafkaTemplate(producerFactory: DefaultKafkaProducerFactory<String, String>): KafkaTemplate<String, String> =
        KafkaTemplate(producerFactory)
}

@ConfigurationProperties("app.kafka")
class PostKafkaProperties {
    lateinit var bootstrapServers: String
    var sendTimeoutMs: Long = 10_000
}
