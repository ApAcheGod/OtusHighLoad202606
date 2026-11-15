package ru.otus.highload.feed.messaging

import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

@Configuration
@EnableConfigurationProperties(FeedKafkaProperties::class)
class KafkaConfig {
    private val log = LoggerFactory.getLogger(KafkaConfig::class.java)

    @Bean
    fun kafkaAdmin(props: FeedKafkaProperties): KafkaAdmin =
        KafkaAdmin(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to props.bootstrapServers))

    @Bean
    fun producerFactory(props: FeedKafkaProperties): DefaultKafkaProducerFactory<String, String> =
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

    @Bean
    fun consumerFactory(props: FeedKafkaProperties): DefaultKafkaConsumerFactory<String, String> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to props.bootstrapServers,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            )
        )

    @Bean
    fun feedListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        meterRegistry: MeterRegistry,
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val consumerErrors = Counter.builder("kafka.consumer.errors").register(meterRegistry)
        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory)
            setConcurrency(3)
            setCommonErrorHandler(
                DefaultErrorHandler({ record, ex ->
                    consumerErrors.increment()
                    log.error(
                        "Kafka listener failed: topic={}, partition={}, offset={}",
                        record.topic(), record.partition(), record.offset(), ex,
                    )
                }, FixedBackOff(1_000L, 3L)),
            )
        }
    }
}

@ConfigurationProperties("app.kafka")
class FeedKafkaProperties {
    lateinit var bootstrapServers: String
    var sendTimeoutMs: Long = 10_000
}
