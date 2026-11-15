package ru.otus.highload.feed.messaging

import io.grpc.StatusRuntimeException
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import ru.otus.highload.feed.messaging.FeedTopics.POST_EVENTS
import ru.otus.highload.grpc.friend.v1.FriendServiceGrpc
import ru.otus.highload.grpc.friend.v1.ListRequest
import ru.otus.highload.grpc.post.v1.GetByIdRequest
import ru.otus.highload.grpc.post.v1.PostServiceGrpc
import ru.otus.highload.utils.events.FeedUserEvent
import ru.otus.highload.utils.events.FeedUserOperation
import ru.otus.highload.utils.events.PostEvent
import ru.otus.highload.utils.events.PostEventOperation
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Component
class PostEventsConsumer(
    private val friendServiceStub: FriendServiceGrpc.FriendServiceBlockingStub,
    private val postServiceStub: PostServiceGrpc.PostServiceBlockingStub,
    private val producer: PostEventProducer,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(PostEventsConsumer::class.java)

    @KafkaListener(
        topics = [POST_EVENTS],
        groupId = "feed-fanout",
        containerFactory = "feedListenerContainerFactory",
    )
    fun onPostEvent(message: String) {
        val event = mapper.readValue(message, PostEvent::class.java)
        when (event.operation) {
            PostEventOperation.CREATED -> fanoutCreated(event)
            PostEventOperation.DELETED -> fanoutDeleted(event)
        }
    }

    private fun fanoutCreated(event: PostEvent) {
        if (!postExists(event.postId)) {
            log.info("post.events {} skipped: post deleted before fanout", event.postId)
            return
        }
        val friends = friendIds(event.authorId)
        if (friends.isEmpty()) return
        producer.userEvents(
            friends.map { friendId ->
                friendId.toString() to FeedUserEvent(
                    operation = FeedUserOperation.POSTED,
                    postId = event.postId,
                    postText = event.text,
                    author_user_id = event.authorId,
                    createdAtMs = event.createdAtMs,
                )
            }
        )
        log.debug("post.events CREATED {}: {} friends → feed.user.events POSTED", event.postId, friends.size)
    }

    private fun fanoutDeleted(event: PostEvent) {
        val friends = friendIds(event.authorId)
        if (friends.isEmpty()) return
        producer.userEvents(
            friends.map { friendId ->
                friendId.toString() to FeedUserEvent(
                    operation = FeedUserOperation.REMOVED,
                    postId = event.postId,
                    createdAtMs = event.createdAtMs,
                )
            }
        )
        log.debug("post.events DELETED {}: {} friends → feed.user.events REMOVED", event.postId, friends.size)
    }

    private fun postExists(postId: String): Boolean = runCatching {
        postServiceStub.getById(GetByIdRequest.newBuilder().setId(postId).build())
        true
    }.getOrElse { ex ->
        if (ex is StatusRuntimeException && ex.status.code == io.grpc.Status.Code.NOT_FOUND) false else throw ex
    }

    private fun friendIds(authorId: String): List<UUID> =
        friendServiceStub.list(ListRequest.newBuilder().setUserId(authorId).build())
            .friendIdsList.map(UUID::fromString)
}
