package ru.otus.highload.app.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.otus.highload.app.model.RegisterRequest
import ru.otus.highload.app.model.User
import ru.otus.highload.app.model.UserResponse
import ru.otus.highload.app.service.UserService
import java.util.UUID

@RestController
class UserController(
    private val userService: UserService,
) {
    @PostMapping("/user/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<Map<String, String>> {
        val userId = userService.register(request)
        return ResponseEntity.ok(mapOf("user_id" to userId.toString()))
    }

    @GetMapping("/user/get/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<UserResponse> {
        val user = userService.getById(id)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        return ResponseEntity.ok(user.toResponse())
    }

    @GetMapping("/user/search")
    fun search(
        @RequestParam("first_name") firstName: String,
        @RequestParam("last_name") lastName: String,
    ): List<UserResponse> = userService.search(firstName, lastName).map { it.toResponse() }

    private fun User.toResponse() = UserResponse(
        id = id,
        firstName = firstName,
        secondName = secondName,
        birthdate = birthdate,
        gender = gender,
        interests = interests,
        biography = biography,
        city = city,
    )
}
