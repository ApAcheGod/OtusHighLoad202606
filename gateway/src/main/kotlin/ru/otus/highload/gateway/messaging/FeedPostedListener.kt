package ru.otus.highload.gateway.messaging

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.stereotype.Component
import org.springframework.util.backoff.FixedBackOff
import ru.otus.highload.gateway.ws.FeedPostedWebSocketHandler
import ru.otus.highload.utils.events.FeedUserEvent
import ru.otus.highload.utils.events.FeedUserOperation
import tools.jackson.databind.ObjectMapper
import java.util.UUID


@Component
class FeedPostedListener(
    private val handler: FeedPostedWebSocketHandler,
    private val mapper: ObjectMapper,
    meterRegistry: MeterRegistry,
) {
    private val received = Counter.builder("ws.events.received").register(meterRegistry)
    private val noSession = Counter.builder("ws.events.no_session").register(meterRegistry)
    private val removedSkipped = Counter.builder("ws.events.removed_skipped").register(meterRegistry)

    @KafkaListener(
        topics = [USER_EVENTS],
        groupId = "ws-gateway-\${random.uuid}",
        containerFactory = "feedListenerContainerFactory",
    )
    fun onUserEvent(record: org.apache.kafka.clients.consumer.ConsumerRecord<String, String>) {
        received.increment()
        val targetUserId = record.key() ?: return
        val event = mapper.readValue(record.value(), FeedUserEvent::class.java)
        if (event.operation != FeedUserOperation.POSTED) {
            removedSkipped.increment()
            return
        }
        val json = mapper.writeValueAsString(
            mapOf(
                "postId" to event.postId,
                "postText" to event.postText,
                "author_user_id" to event.author_user_id,
            )
        )
        if (handler.pushToUser(UUID.fromString(targetUserId), json) == 0) {
            noSession.increment()
        }
    }

    companion object {
        const val USER_EVENTS = "feed.user.events"
    }
}

@Configuration
class GatewayKafkaConfig {
    @Bean
    fun consumerFactory(
        @Value("\${spring.kafka.bootstrap-servers}") bootstrapServers: String,
    ): DefaultKafkaConsumerFactory<String, String> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            )
        )

    @Bean
    fun feedListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
    ): ConcurrentKafkaListenerContainerFactory<String, String> =
        ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory)
            setConcurrency(3)
            setCommonErrorHandler(DefaultErrorHandler(FixedBackOff(1_000L, 3L)))
        }
}
