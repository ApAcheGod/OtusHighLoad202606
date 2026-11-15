package ru.otus.highload.post.messaging

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import ru.otus.highload.utils.events.PostEvent
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Component
class PostEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val mapper: ObjectMapper,
    meterRegistry: MeterRegistry,
    appKafkaProperties: PostKafkaProperties,
) {
    private val log = LoggerFactory.getLogger(PostEventProducer::class.java)
    private val sendTimeoutMs = appKafkaProperties.sendTimeoutMs
    private val produceTimer = Timer.builder("kafka.produce.duration")
        .publishPercentileHistogram()
        .register(meterRegistry)
    private val produceFailures = Counter.builder("kafka.produce.failures")
        .register(meterRegistry)

    fun postEvent(event: PostEvent) {
        send(FeedTopics.POST_EVENTS, event.postId, event)
    }

    private fun send(topic: String, key: String, event: Any): CompletableFuture<SendResult<String, String>> {
        val sample = Timer.start()
        return kafkaTemplate.send(topic, key, mapper.writeValueAsString(event))
            .whenComplete { result, ex ->
                sample.stop(produceTimer)
                if (ex != null) {
                    produceFailures.increment()
                    log.error("Kafka send failed: topic={}, key={}", topic, key, ex)
                } else {
                    log.debug(
                        "Kafka sent: topic={}, key={}, partition={}, offset={}",
                        topic, key, result.recordMetadata.partition(), result.recordMetadata.offset(),
                    )
                }
            }
    }
}
