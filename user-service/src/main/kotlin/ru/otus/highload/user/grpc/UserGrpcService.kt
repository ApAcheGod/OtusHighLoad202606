package ru.otus.highload.user.grpc

import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.grpc.server.service.GrpcService
import org.springframework.security.crypto.password.PasswordEncoder
import ru.otus.highload.grpc.user.v1.CountRequest
import ru.otus.highload.grpc.user.v1.CountResponse
import ru.otus.highload.grpc.user.v1.Gender as GrpcGender
import ru.otus.highload.grpc.user.v1.GetByIdRequest
import ru.otus.highload.grpc.user.v1.GetByIdResponse
import ru.otus.highload.grpc.user.v1.RegisterRequest
import ru.otus.highload.grpc.user.v1.RegisterResponse
import ru.otus.highload.grpc.user.v1.SearchRequest
import ru.otus.highload.grpc.user.v1.SearchResponse
import ru.otus.highload.grpc.user.v1.UserProfile
import ru.otus.highload.grpc.user.v1.UserServiceGrpc
import ru.otus.highload.grpc.user.v1.ValidateCredentialsRequest
import ru.otus.highload.grpc.user.v1.ValidateCredentialsResponse
import ru.otus.highload.user.model.Gender
import ru.otus.highload.user.model.User
import ru.otus.highload.user.repository.UserStore
import ru.otus.highload.utils.uuidV7
import java.time.LocalDate
import java.util.UUID

@GrpcService
class UserGrpcService(
    private val userStore: UserStore,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.search.limit:50}") private val defaultSearchLimit: Int,
) : UserServiceGrpc.UserServiceImplBase() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun register(request: RegisterRequest, responseObserver: StreamObserver<RegisterResponse>) {
        val user = User(
            id = uuidV7(),
            firstName = request.firstName,
            secondName = request.secondName,
            birthdate = LocalDate.parse(request.birthdate),
            gender = request.gender.toModel(),
            interests = request.interests.takeIf { it.isNotEmpty() },
            biography = request.biography.takeIf { it.isNotEmpty() },
            city = request.city.takeIf { it.isNotEmpty() },
            passwordHash = passwordEncoder.encode(request.password) ?: error("Password encoding failed"),
        )
        userStore.save(user)
        log.info("user registered: userId={} name={} {}", user.id, user.firstName, user.secondName)
        respond(responseObserver, RegisterResponse.newBuilder().setUserId(user.id.toString()).build())
    }

    override fun getById(request: GetByIdRequest, responseObserver: StreamObserver<GetByIdResponse>) {
        val user = userStore.findById(UUID.fromString(request.id))
        val builder = GetByIdResponse.newBuilder()
        user?.let { builder.user = it.toProfile() }
        respond(responseObserver, builder.build())
    }

    override fun search(request: SearchRequest, responseObserver: StreamObserver<SearchResponse>) {
        val limit = if (request.limit > 0) request.limit else defaultSearchLimit
        val users = userStore.search(request.firstName, request.secondName, limit)
        respond(
            responseObserver,
            SearchResponse.newBuilder().addAllUsers(users.map { it.toProfile() }).build()
        )
    }

    override fun validateCredentials(
        request: ValidateCredentialsRequest,
        responseObserver: StreamObserver<ValidateCredentialsResponse>,
    ) {
        val user = userStore.findById(UUID.fromString(request.id))
        val valid = user != null && passwordEncoder.matches(request.password, user.passwordHash)
        respond(responseObserver, ValidateCredentialsResponse.newBuilder().setValid(valid).build())
    }

    override fun count(request: CountRequest, responseObserver: StreamObserver<CountResponse>) {
        respond(responseObserver, CountResponse.newBuilder().setCount(userStore.count()).build())
    }

    private fun User.toProfile(): UserProfile {
        val builder = UserProfile.newBuilder()
            .setId(id.toString())
            .setFirstName(firstName)
            .setSecondName(secondName)
            .setBirthdate(birthdate.toString())
            .setGender(gender.toGrpc())
        interests?.let { builder.setInterests(it) }
        biography?.let { builder.setBiography(it) }
        city?.let { builder.setCity(it) }
        return builder.build()
    }

    private fun Gender.toGrpc(): GrpcGender = when (this) {
        Gender.MALE -> GrpcGender.MALE
        Gender.FEMALE -> GrpcGender.FEMALE
    }

    private fun GrpcGender.toModel(): Gender = when (this) {
        GrpcGender.MALE -> Gender.MALE
        GrpcGender.FEMALE -> Gender.FEMALE
        else -> throw IllegalArgumentException("gender is required")
    }

    private fun <T> respond(observer: StreamObserver<T>, value: T) {
        observer.onNext(value)
        observer.onCompleted()
    }
}
