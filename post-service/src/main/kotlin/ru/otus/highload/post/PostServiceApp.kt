package ru.otus.highload.post

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PostServiceApp

fun main(args: Array<String>) {
    runApplication<PostServiceApp>(*args)
}
