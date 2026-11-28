package ru.otus.highload.counter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CounterServiceApp

fun main(args: Array<String>) {
    runApplication<CounterServiceApp>(*args)
}
