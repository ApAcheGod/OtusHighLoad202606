package ru.otus.highload.app.messaging

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import ru.otus.highload.app.messaging.FeedTopics.USER_EVENTS
import ru.otus.highload.app.service.FeedCacheService
import ru.otus.highload.utils.events.FeedUserEvent
import ru.otus.highload.utils.events.FeedUserOperation.POSTED
import ru.otus.highload.utils.events.FeedUserOperation.REMOVED
import tools.jackson.databind.ObjectMapper
import java.util.UUID


@Component
class FeedCacheUpdater(
    private val feedCacheService: FeedCacheService,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(FeedCacheUpdater::class.java)

    @KafkaListener(
        topics = [USER_EVENTS],
        groupId = "feed-cache",
        containerFactory = "feedListenerContainerFactory",
    )
    fun onUserEvent(record: ConsumerRecord<String, String>) {
        val targetUserId = record.key()
        if (targetUserId == null) {
            log.warn("feed.user.events without key (targetUserId) skipped")
            return
        }
        val event = mapper.readValue(record.value(), FeedUserEvent::class.java)
        val feedOwnerId = UUID.fromString(targetUserId)
        when (event.operation) {
            POSTED -> feedCacheService.addPost(
                feedOwnerId = feedOwnerId,
                postId = UUID.fromString(event.postId),
                authorId = UUID.fromString(event.author_user_id!!),
                score = event.createdAtMs!!,
            )
            REMOVED -> feedCacheService.removePostFromFeed(
                feedOwnerId = feedOwnerId,
                postId = UUID.fromString(event.postId),
                score = event.createdAtMs,
            )
        }
    }
}
