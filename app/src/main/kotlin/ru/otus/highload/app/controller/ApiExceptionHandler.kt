package ru.otus.highload.app.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.Instant

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request")

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.BAD_REQUEST, "Malformed request body")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<Map<String, Any>> =
        error(
            HttpStatus.BAD_REQUEST,
            "Invalid value for parameter '${ex.name}': ${ex.value}",
        )

    @ExceptionHandler(IllegalStateException::class)
    fun handleUnauthorized(ex: IllegalStateException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.UNAUTHORIZED, ex.message ?: "Unauthorized")

    private fun error(status: HttpStatus, message: String): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(status).body(
            mapOf(
                "message" to message,
                "code" to status.value(),
                "timestamp" to Instant.now().toString(),
            )
        )
}
