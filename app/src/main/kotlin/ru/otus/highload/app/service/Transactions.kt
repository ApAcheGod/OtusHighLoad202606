package ru.otus.highload.app.service

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/** Выполняет действие после коммита текущей транзакции (или сразу, если транзакции нет). */
fun afterCommit(action: () -> Unit) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                action()
            }
        })
    } else {
        action()
    }
}
