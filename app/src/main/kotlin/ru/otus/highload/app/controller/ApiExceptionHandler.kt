package ru.otus.highload.app.controller

import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
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

    @ExceptionHandler(HttpClientErrorException::class, HttpServerErrorException::class)
    fun handleChatServiceError(ex: org.springframework.web.client.HttpStatusCodeException): ResponseEntity<ByteArray> =
        ResponseEntity.status(ex.statusCode)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(ex.responseBodyAsByteArray)

    @ExceptionHandler(ResourceAccessException::class)
    fun handleChatServiceUnavailable(ex: ResourceAccessException): ResponseEntity<Map<String, Any>> =
        error(HttpStatus.SERVICE_UNAVAILABLE, "Chat service unavailable")

    @ExceptionHandler(StatusRuntimeException::class)
    fun handleGrpc(ex: StatusRuntimeException): ResponseEntity<Map<String, Any>> =
        when (ex.status.code) {
            Status.Code.NOT_FOUND -> error(HttpStatus.NOT_FOUND, ex.status.description ?: "Not found")
            Status.Code.INVALID_ARGUMENT -> error(HttpStatus.BAD_REQUEST, ex.status.description ?: "Bad request")
            Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED ->
                error(HttpStatus.SERVICE_UNAVAILABLE, "Backend service unavailable")
            else -> error(HttpStatus.INTERNAL_SERVER_ERROR, ex.message ?: "gRPC error")
        }

    private fun error(status: HttpStatus, message: String): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(status).body(
            mapOf(
                "message" to message,
                "code" to status.value(),
                "timestamp" to Instant.now().toString(),
            )
        )
}
