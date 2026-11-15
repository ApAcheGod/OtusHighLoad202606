package ru.otus.highload.friend.grpc

import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import org.springframework.grpc.server.service.GrpcService
import org.springframework.transaction.annotation.Transactional
import ru.otus.highload.friend.repository.FriendRepository
import ru.otus.highload.grpc.friend.v1.AddRequest
import ru.otus.highload.grpc.friend.v1.AddResponse
import ru.otus.highload.grpc.friend.v1.FriendServiceGrpc
import ru.otus.highload.grpc.friend.v1.ListRequest
import ru.otus.highload.grpc.friend.v1.ListResponse
import ru.otus.highload.grpc.friend.v1.RemoveRequest
import ru.otus.highload.grpc.friend.v1.RemoveResponse
import ru.otus.highload.grpc.user.v1.GetByIdRequest
import ru.otus.highload.grpc.user.v1.UserServiceGrpc
import java.util.UUID

@GrpcService
class FriendGrpcService(
    private val friendRepository: FriendRepository,
    private val userServiceStub: UserServiceGrpc.UserServiceBlockingStub,
) : FriendServiceGrpc.FriendServiceImplBase() {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun add(request: AddRequest, responseObserver: StreamObserver<AddResponse>) {
        val userId = uuid("user_id", request.userId)
        val friendId = uuid("friend_id", request.friendId)

        requireExists(userId, "User")
        requireExists(friendId, "Friend")
        if (friendId == userId) {
            throw Status.INVALID_ARGUMENT.withDescription("Cannot add yourself as friend").asRuntimeException()
        }

        friendRepository.saveMutual(userId, friendId)
        log.info("friend added: user={} friend={}", userId, friendId)
        respond(responseObserver, AddResponse.getDefaultInstance())
    }

    @Transactional
    override fun remove(request: RemoveRequest, responseObserver: StreamObserver<RemoveResponse>) {
        val userId = uuid("user_id", request.userId)
        val friendId = uuid("friend_id", request.friendId)
        friendRepository.deleteMutual(userId, friendId)
        log.info("friend removed: user={} friend={}", userId, friendId)
        respond(responseObserver, RemoveResponse.getDefaultInstance())
    }

    override fun list(request: ListRequest, responseObserver: StreamObserver<ListResponse>) {
        val friendIds = friendRepository.findFriendIds(uuid("user_id", request.userId))
        respond(
            responseObserver,
            ListResponse.newBuilder().addAllFriendIds(friendIds.map { it.toString() }).build()
        )
    }

    private fun requireExists(userId: UUID, label: String) {
        val found = runCatching {
            userServiceStub.getById(GetByIdRequest.newBuilder().setId(userId.toString()).build())
        }.getOrNull()?.user
        if (found == null || found.id.isEmpty()) {
            throw Status.INVALID_ARGUMENT.withDescription("$label not found: $userId").asRuntimeException()
        }
    }

    private fun uuid(field: String, value: String): UUID = try {
        UUID.fromString(value)
    } catch (ex: IllegalArgumentException) {
        throw Status.INVALID_ARGUMENT.withDescription("invalid $field: $value").asRuntimeException()
    }

    private fun <T> respond(observer: StreamObserver<T>, value: T) {
        observer.onNext(value)
        observer.onCompleted()
    }
}
