package ru.otus.highload.chat.messaging

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import ru.otus.highload.chat.repository.DialogEventStorage
import ru.otus.highload.utils.events.CounterEvent
import ru.otus.highload.utils.events.CounterTopics
import tools.jackson.databind.ObjectMapper

@Component
class CompensationConsumer(
    private val storage: DialogEventStorage,
    @Qualifier("counterEventObjectMapper") private val mapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [CounterTopics.COMPENSATIONS],
        groupId = "chat-counter-compensations",
        containerFactory = "chatListenerContainerFactory",
    )
    fun onCompensation(message: String, acknowledgment: Acknowledgment) {
        val event = mapper.readValue(message, CounterEvent::class.java)
        storage.applyCompensation(event)
        acknowledgment.acknowledge()
    }
}
