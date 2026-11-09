package ru.otus.highload.user.repository

import ru.otus.highload.user.model.User
import java.util.UUID

interface UserStore {
    fun save(user: User)
    fun findById(id: UUID): User?
    fun search(firstName: String, secondName: String, limit: Int): List<User>
    fun count(): Long
}
