package ru.otus.highload.friend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FriendServiceApp

fun main(args: Array<String>) {
    runApplication<FriendServiceApp>(*args)
}
