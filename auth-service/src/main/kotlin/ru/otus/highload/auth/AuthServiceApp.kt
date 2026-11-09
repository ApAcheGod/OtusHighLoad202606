package ru.otus.highload.auth

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AuthServiceApp

fun main(args: Array<String>) {
    runApplication<AuthServiceApp>(*args)
}
