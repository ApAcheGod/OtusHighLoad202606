package ru.otus.highload.chat

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ChatServiceApp

fun main(args: Array<String>) {
    runApplication<ChatServiceApp>(*args)
}
