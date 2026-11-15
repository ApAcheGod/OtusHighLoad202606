package ru.otus.highload.app.service

import io.grpc.StatusRuntimeException
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import ru.otus.highload.app.model.Gender
import ru.otus.highload.app.model.RegisterRequest
import ru.otus.highload.app.model.User
import ru.otus.highload.grpc.user.v1.CountRequest
import ru.otus.highload.grpc.user.v1.GetByIdRequest
import ru.otus.highload.grpc.user.v1.RegisterRequest as GrpcRegisterRequest
import ru.otus.highload.grpc.user.v1.SearchRequest
import ru.otus.highload.grpc.user.v1.UserServiceGrpc
import ru.otus.highload.grpc.user.v1.ValidateCredentialsRequest
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

@Service
class UserService(
    private val userStub: UserServiceGrpc.UserServiceBlockingStub,
    meterRegistry: MeterRegistry,
) {
    private val registrationsCounter = meterRegistry.counter("app_registrations_total", "description", "Total number of user registrations")

    fun register(request: RegisterRequest): UUID {
        val response = userStub.withDeadlineAfter(RPC_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .register(
                GrpcRegisterRequest.newBuilder()
                    .setFirstName(request.firstName)
                    .setSecondName(request.secondName)
                    .setBirthdate(request.birthdate.toString())
                    .setGender(
                        when (request.gender) {
                            Gender.MALE -> ru.otus.highload.grpc.user.v1.Gender.MALE
                            Gender.FEMALE -> ru.otus.highload.grpc.user.v1.Gender.FEMALE
                        }
                    )
                    .apply {
                        request.interests?.let { setInterests(it) }
                        request.biography?.let { setBiography(it) }
                        request.city?.let { setCity(it) }
                    }
                    .setPassword(request.password)
                    .build()
            )
        registrationsCounter.increment()
        return UUID.fromString(response.userId)
    }

    fun getById(id: UUID): User? {
        val response = userStub.withDeadlineAfter(RPC_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .getById(GetByIdRequest.newBuilder().setId(id.toString()).build())
        return response.user.takeIf { it.id.isNotEmpty() }?.toModel()
    }

    fun search(firstName: String, secondName: String): List<User> {
        val response = userStub.withDeadlineAfter(RPC_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .search(SearchRequest.newBuilder().setFirstName(firstName).setSecondName(secondName).build())
        return response.usersList.map { it.toModel() }
    }

    fun validatePassword(id: UUID, password: String): Boolean {
        val response = userStub.withDeadlineAfter(VALIDATE_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .validateCredentials(ValidateCredentialsRequest.newBuilder().setId(id.toString()).setPassword(password).build())
        return response.valid
    }

    fun countUsers(): Long = try {
        userStub.withDeadlineAfter(RPC_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .count(CountRequest.getDefaultInstance()).count
    } catch (ex: StatusRuntimeException) {
        0L
    }

    private fun ru.otus.highload.grpc.user.v1.UserProfile.toModel(): User =
        User(
            id = UUID.fromString(id),
            firstName = firstName,
            secondName = secondName,
            birthdate = LocalDate.parse(birthdate),
            gender = when (gender) {
                ru.otus.highload.grpc.user.v1.Gender.MALE -> Gender.MALE
                ru.otus.highload.grpc.user.v1.Gender.FEMALE -> Gender.FEMALE
                else -> throw IllegalArgumentException("unexpected gender $gender")
            },
            interests = interests.takeIf { it.isNotEmpty() },
            biography = biography.takeIf { it.isNotEmpty() },
            city = city.takeIf { it.isNotEmpty() },
            passwordHash = "",
        )

    companion object {
        private val RPC_TIMEOUT = Duration.ofMillis(500)
        private val VALIDATE_TIMEOUT = Duration.ofSeconds(2)
    }
}
