package ru.otus.highload.app.controller

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import ru.otus.highload.app.model.LoginRequest
import ru.otus.highload.app.service.TokenService
import ru.otus.highload.app.service.UserService

@RestController
class AuthController(
    private val userService: UserService,
    private val tokenService: TokenService,
    meterRegistry: MeterRegistry,
) {
    private val loginSuccessCounter = Counter.builder("app_login_success_total")
        .description("Total number of successful logins")
        .register(meterRegistry)

    private val loginFailureCounter = Counter.builder("app_login_failure_total")
        .description("Total number of failed login attempts")
        .register(meterRegistry)

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Map<String, String>> {
        if (!userService.validatePassword(request.id, request.password)) {
            loginFailureCounter.increment()
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val token = tokenService.createToken(request.id)
        loginSuccessCounter.increment()
        return ResponseEntity.ok(mapOf("token" to token.toString()))
    }
}
