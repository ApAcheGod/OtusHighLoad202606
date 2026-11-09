package ru.otus.highload.auth.grpc

import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.grpc.server.service.GrpcService
import ru.otus.highload.auth.config.RequestIdMetadataInterceptor
import ru.otus.highload.grpc.session.v1.AuthenticateRequest
import ru.otus.highload.grpc.session.v1.AuthenticateResponse
import ru.otus.highload.grpc.session.v1.CountRequest
import ru.otus.highload.grpc.session.v1.CountResponse
import ru.otus.highload.grpc.session.v1.CreateRequest
import ru.otus.highload.grpc.session.v1.CreateResponse
import ru.otus.highload.grpc.session.v1.DeleteRequest
import ru.otus.highload.grpc.session.v1.DeleteResponse
import ru.otus.highload.grpc.session.v1.SessionServiceGrpc
import ru.otus.highload.utils.session.SessionStore
import ru.otus.highload.utils.uuidV7
import org.slf4j.LoggerFactory
import java.util.UUID

@GrpcService
class SessionGrpcService(
    private val sessionStore: SessionStore,
) : SessionServiceGrpc.SessionServiceImplBase() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun create(request: CreateRequest, responseObserver: StreamObserver<CreateResponse>) {
        val userId = UUID.fromString(request.userId)
        val token = uuidV7()
        try {
            sessionStore.create(token, userId)
        } catch (ex: RedisConnectionFailureException) {
            throw Status.UNAVAILABLE.withDescription("Session storage unavailable").withCause(ex).asRuntimeException()
        }
        log.info("session created: userId={}", userId)
        respond(responseObserver, CreateResponse.newBuilder().setToken(token.toString()).build())
    }

    override fun authenticate(request: AuthenticateRequest, responseObserver: StreamObserver<AuthenticateResponse>) {
        val userId = try {
            sessionStore.resolveUserId(UUID.fromString(request.token))
        } catch (ex: RedisConnectionFailureException) {
            throw Status.UNAVAILABLE.withDescription("Session storage unavailable").withCause(ex).asRuntimeException()
        }
        log.info("authenticate: tokenKnown={}", userId != null)
        respond(
            responseObserver,
            AuthenticateResponse.newBuilder().setUserId(userId?.toString() ?: "").build()
        )
    }

    override fun delete(request: DeleteRequest, responseObserver: StreamObserver<DeleteResponse>) {
        sessionStore.delete(UUID.fromString(request.token))
        respond(responseObserver, DeleteResponse.getDefaultInstance())
    }

    override fun count(request: CountRequest, responseObserver: StreamObserver<CountResponse>) {
        val count = try {
            sessionStore.count()
        } catch (ex: RedisConnectionFailureException) {
            throw Status.UNAVAILABLE.withDescription("Session storage unavailable").withCause(ex).asRuntimeException()
        }
        respond(responseObserver, CountResponse.newBuilder().setCount(count).build())
    }

    private fun <T> respond(observer: StreamObserver<T>, value: T) {
        observer.onNext(value)
        observer.onCompleted()
    }
}
