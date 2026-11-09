package ru.otus.highload.feed.grpc

import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import org.springframework.grpc.server.service.GrpcService
import ru.otus.highload.feed.service.FeedCacheService
import ru.otus.highload.feed.service.FeedItemData
import ru.otus.highload.grpc.feed.v1.FeedServiceGrpc
import ru.otus.highload.grpc.feed.v1.GetFeedRequest
import ru.otus.highload.grpc.feed.v1.GetFeedResponse
import ru.otus.highload.grpc.feed.v1.RemoveAuthorRequest
import ru.otus.highload.grpc.feed.v1.RemoveAuthorResponse
import ru.otus.highload.grpc.feed.v1.WarmRequest
import ru.otus.highload.grpc.feed.v1.WarmResponse
import ru.otus.highload.grpc.post.v1.GetByIdsRequest
import ru.otus.highload.grpc.post.v1.GetFriendFeedCandidatesRequest
import ru.otus.highload.grpc.post.v1.PostServiceGrpc
import java.util.UUID

@GrpcService
class FeedGrpcService(
    private val feedCacheService: FeedCacheService,
    private val postServiceStub: PostServiceGrpc.PostServiceBlockingStub,
) : FeedServiceGrpc.FeedServiceImplBase() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getFeed(request: GetFeedRequest, responseObserver: StreamObserver<GetFeedResponse>) {
        val offset = request.offset
        val limit = request.limit
        if (offset < 0) {
            throw Status.INVALID_ARGUMENT.withDescription("offset must be >= 0").asRuntimeException()
        }
        if (limit < 1) {
            throw Status.INVALID_ARGUMENT.withDescription("limit must be >= 1").asRuntimeException()
        }
        val userId = uuid("user_id", request.userId)

        if (feedCacheService.getFeedPostIds(userId, 0, 1) == null) {
            warmFromDatabase(userId)
        }

        val postIds = feedCacheService.getFeedPostIds(userId, offset, limit)
        if (postIds.isNullOrEmpty()) {
            respond(responseObserver, GetFeedResponse.getDefaultInstance())
            return
        }

        val posts = postServiceStub.getByIds(
            GetByIdsRequest.newBuilder().addAllIds(postIds.map { it.toString() }).build()
        ).postsList
        val postMap = posts.associateBy { it.id }
        val ordered = postIds.mapNotNull { postMap[it.toString()] }
        respond(responseObserver, GetFeedResponse.newBuilder().addAllPosts(ordered).build())
    }

    override fun warm(request: WarmRequest, responseObserver: StreamObserver<WarmResponse>) {
        val userId = uuid("user_id", request.userId)
        val items = request.itemsList.map {
            FeedItemData(
                postId = uuid("post_id", it.postId),
                authorId = uuid("author_id", it.authorId),
                score = it.score,
            )
        }
        feedCacheService.warmFeed(userId, items)
        respond(responseObserver, WarmResponse.getDefaultInstance())
    }

    override fun removeAuthor(request: RemoveAuthorRequest, responseObserver: StreamObserver<RemoveAuthorResponse>) {
        feedCacheService.removeAuthorFromFeed(
            uuid("owner_id", request.ownerId),
            uuid("author_id", request.authorId),
        )
        respond(responseObserver, RemoveAuthorResponse.getDefaultInstance())
    }

    private fun warmFromDatabase(userId: UUID) {
        val candidates = postServiceStub.getFriendFeedCandidates(
            GetFriendFeedCandidatesRequest.newBuilder()
                .setUserId(userId.toString())
                .setLimit(feedCacheService.maxSize)
                .build()
        ).itemsList.map {
            FeedItemData(
                postId = UUID.fromString(it.postId),
                authorId = UUID.fromString(it.authorId),
                score = it.score,
            )
        }
        feedCacheService.warmFeed(userId, candidates)
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
