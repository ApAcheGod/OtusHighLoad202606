package ru.otus.highload.app.controller

import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.otus.highload.app.config.currentUserId
import ru.otus.highload.app.model.PostCreateRequest
import ru.otus.highload.app.model.PostResponse
import ru.otus.highload.app.model.PostUpdateRequest
import ru.otus.highload.grpc.feed.v1.FeedServiceGrpc
import ru.otus.highload.grpc.feed.v1.GetFeedRequest
import ru.otus.highload.grpc.post.v1.CreateRequest
import ru.otus.highload.grpc.post.v1.DeleteRequest
import ru.otus.highload.grpc.post.v1.GetByIdRequest
import ru.otus.highload.grpc.post.v1.Post as GrpcPost
import ru.otus.highload.grpc.post.v1.PostServiceGrpc
import ru.otus.highload.grpc.post.v1.UpdateRequest
import java.util.UUID

@RestController
class PostController(
    private val postServiceStub: PostServiceGrpc.PostServiceBlockingStub,
    private val feedServiceStub: FeedServiceGrpc.FeedServiceBlockingStub,
) {

    @PostMapping("/post/create")
    fun createPost(@RequestBody request: PostCreateRequest): ResponseEntity<UUID> {
        val response = postServiceStub.create(
            CreateRequest.newBuilder()
                .setAuthorId(currentUserId().toString())
                .setText(request.text)
                .build()
        )
        return ResponseEntity.ok(UUID.fromString(response.postId))
    }

    @GetMapping("/post/get/{id}")
    fun getPost(@PathVariable id: UUID): ResponseEntity<PostResponse> {
        val post = try {
            postServiceStub.getById(GetByIdRequest.newBuilder().setId(id.toString()).build()).post
        } catch (ex: StatusRuntimeException) {
            if (ex.status.code == Status.Code.NOT_FOUND) return ResponseEntity.notFound().build()
            throw ex
        }
        return ResponseEntity.ok(post.toResponse())
    }

    @PutMapping("/post/update")
    fun updatePost(@RequestBody request: PostUpdateRequest): ResponseEntity<Void> {
        try {
            postServiceStub.update(
                UpdateRequest.newBuilder()
                    .setId(request.id.toString())
                    .setAuthorId(currentUserId().toString())
                    .setText(request.text)
                    .build()
            )
        } catch (ex: StatusRuntimeException) {
            if (ex.status.code == Status.Code.NOT_FOUND) return ResponseEntity.notFound().build()
            throw ex
        }
        return ResponseEntity.ok().build()
    }

    @PutMapping("/post/delete/{id}")
    fun deletePost(@PathVariable id: UUID): ResponseEntity<Void> {
        try {
            postServiceStub.delete(
                DeleteRequest.newBuilder()
                    .setId(id.toString())
                    .setAuthorId(currentUserId().toString())
                    .build()
            )
        } catch (ex: StatusRuntimeException) {
            if (ex.status.code != Status.Code.NOT_FOUND) throw ex
        }
        return ResponseEntity.ok().build()
    }

    @GetMapping("/post/feed")
    fun getFeed(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<List<PostResponse>> {
        val posts = feedServiceStub.getFeed(
            GetFeedRequest.newBuilder()
                .setUserId(currentUserId().toString())
                .setOffset(offset)
                .setLimit(limit)
                .build()
        ).postsList
        return ResponseEntity.ok(posts.map { it.toResponse() })
    }

    private fun GrpcPost.toResponse() = PostResponse(
        id = UUID.fromString(id),
        text = text,
        authorUserId = UUID.fromString(authorId),
    )
}
