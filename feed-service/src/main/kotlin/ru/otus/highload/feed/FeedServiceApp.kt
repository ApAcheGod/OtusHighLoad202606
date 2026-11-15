package ru.otus.highload.feed

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka

@SpringBootApplication
@EnableKafka
class FeedServiceApp

fun main(args: Array<String>) {
    runApplication<FeedServiceApp>(*args)
}
