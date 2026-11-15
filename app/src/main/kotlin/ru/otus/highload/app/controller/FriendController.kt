package ru.otus.highload.app.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.otus.highload.app.config.currentUserId
import ru.otus.highload.grpc.feed.v1.FeedServiceGrpc
import ru.otus.highload.grpc.feed.v1.RemoveAuthorRequest
import ru.otus.highload.grpc.feed.v1.WarmRequest
import ru.otus.highload.grpc.friend.v1.AddRequest
import ru.otus.highload.grpc.friend.v1.FriendServiceGrpc
import ru.otus.highload.grpc.friend.v1.RemoveRequest
import ru.otus.highload.grpc.post.v1.FeedItem
import ru.otus.highload.grpc.post.v1.ListLatestByAuthorRequest
import ru.otus.highload.grpc.post.v1.PostServiceGrpc
import java.util.UUID

@RestController
class FriendController(
    private val friendServiceStub: FriendServiceGrpc.FriendServiceBlockingStub,
    private val postServiceStub: PostServiceGrpc.PostServiceBlockingStub,
    private val feedServiceStub: FeedServiceGrpc.FeedServiceBlockingStub,
    @Value("\${app.feed.max-size:1000}") private val feedMaxSize: Int,
) {

    @PutMapping("/friend/set/{friendUserId}")
    fun setFriend(@PathVariable friendUserId: UUID): ResponseEntity<Void> {
        val userId = currentUserId()
        friendServiceStub.add(
            AddRequest.newBuilder()
                .setUserId(userId.toString())
                .setFriendId(friendUserId.toString())
                .build()
        )
        backfillFeeds(userId, friendUserId)
        return ResponseEntity.ok().build()
    }

    @PutMapping("/friend/delete/{friendUserId}")
    fun deleteFriend(@PathVariable friendUserId: UUID): ResponseEntity<Void> {
        val userId = currentUserId()
        friendServiceStub.remove(
            RemoveRequest.newBuilder()
                .setUserId(userId.toString())
                .setFriendId(friendUserId.toString())
                .build()
        )
        feedServiceStub.removeAuthor(
            RemoveAuthorRequest.newBuilder().setOwnerId(userId.toString()).setAuthorId(friendUserId.toString()).build()
        )
        feedServiceStub.removeAuthor(
            RemoveAuthorRequest.newBuilder().setOwnerId(friendUserId.toString()).setAuthorId(userId.toString()).build()
        )
        return ResponseEntity.ok().build()
    }

    private fun backfillFeeds(userId: UUID, friendUserId: UUID) {
        val friendPosts = latestPosts(friendUserId)
        val myPosts = latestPosts(userId)
        feedServiceStub.warm(
            WarmRequest.newBuilder()
                .setUserId(userId.toString())
                .addAllItems(friendPosts)
                .build()
        )
        feedServiceStub.warm(
            WarmRequest.newBuilder()
                .setUserId(friendUserId.toString())
                .addAllItems(myPosts)
                .build()
        )
    }

    private fun latestPosts(authorId: UUID): List<FeedItem> =
        postServiceStub.listLatestByAuthor(
            ListLatestByAuthorRequest.newBuilder()
                .setAuthorId(authorId.toString())
                .setLimit(feedMaxSize)
                .build()
        ).itemsList
}
