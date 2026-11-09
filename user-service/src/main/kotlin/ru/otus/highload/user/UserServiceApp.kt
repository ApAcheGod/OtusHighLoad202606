package ru.otus.highload.user

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class UserServiceApp

fun main(args: Array<String>) {
    runApplication<UserServiceApp>(*args)
}
