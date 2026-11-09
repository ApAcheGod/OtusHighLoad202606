package ru.otus.highload.post.grpc

import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import org.springframework.grpc.server.service.GrpcService
import org.springframework.transaction.annotation.Transactional
import ru.otus.highload.grpc.post.v1.CreateRequest
import ru.otus.highload.grpc.post.v1.CreateResponse
import ru.otus.highload.grpc.post.v1.DeleteRequest
import ru.otus.highload.grpc.post.v1.DeleteResponse
import ru.otus.highload.grpc.post.v1.GetByIdRequest
import ru.otus.highload.grpc.post.v1.GetByIdResponse
import ru.otus.highload.grpc.post.v1.GetByIdsRequest
import ru.otus.highload.grpc.post.v1.GetByIdsResponse
import ru.otus.highload.grpc.post.v1.GetFriendFeedCandidatesRequest
import ru.otus.highload.grpc.post.v1.GetFriendFeedCandidatesResponse
import ru.otus.highload.grpc.post.v1.ListLatestByAuthorRequest
import ru.otus.highload.grpc.post.v1.ListLatestByAuthorResponse
import ru.otus.highload.grpc.post.v1.Post as GrpcPost
import ru.otus.highload.grpc.post.v1.FeedItem as GrpcFeedItem
import ru.otus.highload.grpc.post.v1.PostServiceGrpc
import ru.otus.highload.grpc.post.v1.UpdateRequest
import ru.otus.highload.grpc.post.v1.UpdateResponse
import ru.otus.highload.post.messaging.PostEventProducer
import ru.otus.highload.post.model.Post
import ru.otus.highload.post.repository.PostRepository
import ru.otus.highload.post.service.FeedItemData
import ru.otus.highload.post.service.afterCommit
import ru.otus.highload.utils.events.PostEvent
import ru.otus.highload.utils.events.PostEventOperation
import ru.otus.highload.utils.uuidV7
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

@GrpcService
class PostGrpcService(
    private val postRepository: PostRepository,
    private val postEventProducer: PostEventProducer,
) : PostServiceGrpc.PostServiceImplBase() {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun create(request: CreateRequest, responseObserver: StreamObserver<CreateResponse>) {
        val authorId = uuid("author_id", request.authorId)
        val postId = uuidV7()
        val createdAt = Instant.now()
        postRepository.save(
            Post(
                id = postId,
                userId = authorId,
                text = request.text,
                createdAt = DateTimeFormatter.ISO_INSTANT.format(createdAt),
                likes = 0,
            )
        )
        val score = createdAt.toEpochMilli()
        afterCommit {
            postEventProducer.postEvent(
                PostEvent(PostEventOperation.CREATED, postId.toString(), authorId.toString(), request.text, score)
            )
        }
        log.info("post created: postId={} author={}", postId, authorId)
        respond(responseObserver, CreateResponse.newBuilder().setPostId(postId.toString()).build())
    }

    override fun getById(request: GetByIdRequest, responseObserver: StreamObserver<GetByIdResponse>) {
        val post = postRepository.findById(uuid("id", request.id))
            ?: throw notFound("post not found: ${request.id}")
        respond(responseObserver, GetByIdResponse.newBuilder().setPost(post.toGrpc()).build())
    }

    override fun getByIds(request: GetByIdsRequest, responseObserver: StreamObserver<GetByIdsResponse>) {
        val ids = request.idsList.map { uuid("ids", it) }
        val posts = postRepository.findByIds(ids)
        respond(
            responseObserver,
            GetByIdsResponse.newBuilder().addAllPosts(posts.map { it.toGrpc() }).build()
        )
    }

    @Transactional
    override fun update(request: UpdateRequest, responseObserver: StreamObserver<UpdateResponse>) {
        val id = uuid("id", request.id)
        val authorId = uuid("author_id", request.authorId)
        val updated = postRepository.update(id, authorId, request.text)
            ?: run {
                val post = postRepository.findById(id) ?: throw notFound("post not found: ${request.id}")
                if (post.userId != authorId) {
                    throw Status.INVALID_ARGUMENT.withDescription("Not post owner").asRuntimeException()
                }
                throw notFound("post not found: ${request.id}")
            }
        respond(responseObserver, UpdateResponse.newBuilder().setPost(updated.toGrpc()).build())
    }

    @Transactional
    override fun delete(request: DeleteRequest, responseObserver: StreamObserver<DeleteResponse>) {
        val id = uuid("id", request.id)
        val authorId = uuid("author_id", request.authorId)
        val post = postRepository.findById(id) ?: throw notFound("post not found: ${request.id}")
        if (post.userId != authorId) {
            throw Status.INVALID_ARGUMENT.withDescription("Not post owner").asRuntimeException()
        }
        val deleted = postRepository.deleteById(id, authorId)
        if (deleted) {
            val score = Instant.parse(post.createdAt).toEpochMilli()
            afterCommit {
                postEventProducer.postEvent(
                    PostEvent(PostEventOperation.DELETED, id.toString(), authorId.toString(), createdAtMs = score)
                )
            }
        }
        respond(responseObserver, DeleteResponse.newBuilder().setDeleted(deleted).build())
    }

    override fun listLatestByAuthor(
        request: ListLatestByAuthorRequest,
        responseObserver: StreamObserver<ListLatestByAuthorResponse>,
    ) {
        val items = postRepository.findLatestByAuthor(
            uuid("author_id", request.authorId),
            request.limit,
        )
        respond(
            responseObserver,
            ListLatestByAuthorResponse.newBuilder().addAllItems(items.map { it.toGrpc() }).build()
        )
    }

    override fun getFriendFeedCandidates(
        request: GetFriendFeedCandidatesRequest,
        responseObserver: StreamObserver<GetFriendFeedCandidatesResponse>,
    ) {
        val items = postRepository.findFriendFeedCandidates(
            uuid("user_id", request.userId),
            request.limit,
        )
        respond(
            responseObserver,
            GetFriendFeedCandidatesResponse.newBuilder().addAllItems(items.map { it.toGrpc() }).build()
        )
    }

    private fun Post.toGrpc(): GrpcPost = GrpcPost.newBuilder()
        .setId(id.toString())
        .setAuthorId(userId.toString())
        .setText(text)
        .setCreatedAtMs(Instant.parse(createdAt).toEpochMilli())
        .setLikes(likes)
        .build()

    private fun FeedItemData.toGrpc(): GrpcFeedItem = GrpcFeedItem.newBuilder()
        .setPostId(postId.toString())
        .setAuthorId(authorId.toString())
        .setScore(score)
        .build()

    private fun uuid(field: String, value: String): UUID = try {
        UUID.fromString(value)
    } catch (ex: IllegalArgumentException) {
        throw Status.INVALID_ARGUMENT.withDescription("invalid $field: $value").asRuntimeException()
    }

    private fun notFound(message: String) = Status.NOT_FOUND.withDescription(message).asRuntimeException()

    private fun <T> respond(observer: StreamObserver<T>, value: T) {
        observer.onNext(value)
        observer.onCompleted()
    }
}
