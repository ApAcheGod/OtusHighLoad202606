package ru.otus.highload.post.messaging

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

object FeedTopics {
    const val POST_EVENTS = "post.events"
    const val USER_EVENTS = "feed.user.events"

    const val PARTITIONS = 6
}

@Configuration
class FeedTopicsConfig {
    @Bean
    fun postEventsTopic(): NewTopic =
        TopicBuilder.name(FeedTopics.POST_EVENTS).partitions(FeedTopics.PARTITIONS).replicas(1).build()

    @Bean
    fun userEventsTopic(): NewTopic =
        TopicBuilder.name(FeedTopics.USER_EVENTS).partitions(FeedTopics.PARTITIONS).replicas(1).build()
}
