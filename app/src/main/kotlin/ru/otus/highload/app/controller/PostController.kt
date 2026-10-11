package ru.otus.highload.app.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.otus.highload.app.model.Post
import ru.otus.highload.app.model.PostCreateRequest
import ru.otus.highload.app.model.PostResponse
import ru.otus.highload.app.model.PostUpdateRequest
import ru.otus.highload.app.service.PostService
import java.util.UUID

@RestController
class PostController(
    private val postService: PostService
) {

    @PostMapping("/post/create")
    fun createPost(@RequestBody request: PostCreateRequest): ResponseEntity<UUID> {
        val postId = postService.createPost(request.text)
        return ResponseEntity.ok(postId)
    }

    @GetMapping("/post/get/{id}")
    fun getPost(@PathVariable id: UUID): ResponseEntity<PostResponse> {
        val post = postService.getPost(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(post.toResponse())
    }

    @PutMapping("/post/update")
    fun updatePost(@RequestBody request: PostUpdateRequest): ResponseEntity<Void> {
        postService.updatePost(request.id, request.text)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok().build()
    }

    @PutMapping("/post/delete/{id}")
    fun deletePost(@PathVariable id: UUID): ResponseEntity<Void> {
        postService.deletePost(id)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/post/feed")
    fun getFeed(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<List<PostResponse>> {
        val posts = postService.getFeed(offset, limit)
        return ResponseEntity.ok(posts.map { it.toResponse() })
    }

    private fun Post.toResponse() = PostResponse(
        id = id,
        text = text,
        authorUserId = userId,
    )
}
