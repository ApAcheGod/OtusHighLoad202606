package ru.otus.highload.chat.messaging

import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties.AckMode
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff
import ru.otus.highload.utils.events.CounterTopics
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

@Configuration
@EnableKafka
@EnableConfigurationProperties(ChatKafkaProperties::class)
class ChatKafkaConfig {
    @Bean
    fun kafkaAdmin(properties: ChatKafkaProperties): KafkaAdmin =
        KafkaAdmin(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to properties.bootstrapServers))

    @Bean
    fun chatProducerFactory(properties: ChatKafkaProperties) = DefaultKafkaProducerFactory<String, String>(
        mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to properties.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
        )
    )

    @Bean
    fun kafkaTemplate(producerFactory: DefaultKafkaProducerFactory<String, String>): KafkaTemplate<String, String> =
        KafkaTemplate(producerFactory)

    @Bean
    fun chatConsumerFactory(properties: ChatKafkaProperties): ConsumerFactory<String, String> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to properties.bootstrapServers,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            )
        )

    @Bean
    fun chatListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
    ) = ConcurrentKafkaListenerContainerFactory<String, String>().apply {
        setConsumerFactory(consumerFactory)
        containerProperties.ackMode = AckMode.MANUAL_IMMEDIATE
        setCommonErrorHandler(DefaultErrorHandler(FixedBackOff(1_000L, FixedBackOff.UNLIMITED_ATTEMPTS)))
    }

    @Bean
    @Qualifier("counterEventObjectMapper")
    fun counterEventObjectMapper(): ObjectMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build()

    @Bean
    fun counterEventsTopic() = TopicBuilder.name(CounterTopics.EVENTS)
        .partitions(6).replicas(1)
        .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
        .build()

    @Bean
    fun counterCompensationsTopic() = TopicBuilder.name(CounterTopics.COMPENSATIONS)
        .partitions(6).replicas(1).build()

    @Bean
    fun counterDltTopic() = TopicBuilder.name(CounterTopics.DLT)
        .partitions(6).replicas(1).build()
}

@ConfigurationProperties("app.kafka")
class ChatKafkaProperties {
    lateinit var bootstrapServers: String
    var sendTimeoutMs: Long = 10_000
}
