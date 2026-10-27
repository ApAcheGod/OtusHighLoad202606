package ru.otus.highload.app.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.otus.highload.app.config.currentUserId
import ru.otus.highload.app.service.FriendService
import java.util.UUID

@RestController
class FriendController(
    private val friendService: FriendService
) {

    @PutMapping("/friend/set/{friendUserId}")
    fun setFriend(@PathVariable friendUserId: UUID): ResponseEntity<Void> {
        friendService.addFriend(currentUserId(), friendUserId)
        return ResponseEntity.ok().build()
    }

    @PutMapping("/friend/delete/{friendUserId}")
    fun deleteFriend(@PathVariable friendUserId: UUID): ResponseEntity<Void> {
        friendService.deleteFriend(currentUserId(), friendUserId)
        return ResponseEntity.ok().build()
    }
}
