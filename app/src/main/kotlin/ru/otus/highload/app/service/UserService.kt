package ru.otus.highload.app.service

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import ru.otus.highload.app.model.RegisterRequest
import ru.otus.highload.app.model.User
import ru.otus.highload.app.repository.UserRepository
import ru.otus.highload.utils.uuidV7
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val meterRegistry: MeterRegistry,
) {
    private val registrationsCounter = meterRegistry.counter("app_registrations_total", "description", "Total number of user registrations")

    fun register(request: RegisterRequest): UUID {
        val id = uuidV7()
        val encodedPassword = passwordEncoder.encode(request.password) ?: error("Password encoding failed")
        val user = User(
            id = id,
            firstName = request.firstName,
            secondName = request.secondName,
            birthdate = request.birthdate,
            gender = request.gender,
            interests = request.interests,
            biography = request.biography,
            city = request.city,
            passwordHash = encodedPassword,
        )
        userRepository.save(user)
        registrationsCounter.increment()
        return id
    }

    fun getById(id: UUID): User? {
        return userRepository.findById(id)
    }

    fun search(firstName: String, secondName: String): List<User> {
        return userRepository.search(firstName, secondName)
    }

    fun validatePassword(id: UUID, password: String): Boolean {
        val user = userRepository.findById(id) ?: return false
        return passwordEncoder.matches(password, user.passwordHash)
    }
}
