package ru.otus.highload.post.config

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import org.slf4j.LoggerFactory
import org.springframework.grpc.server.exception.GrpcExceptionHandler
import org.springframework.stereotype.Component

@Component
class GrpcExceptionAdvice : GrpcExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleException(exception: Throwable): StatusException? {
        if (exception is StatusRuntimeException) return null
        if (exception is StatusException) return null
        log.error("gRPC handler failed", exception)
        return Status.INTERNAL.withDescription(exception.message ?: "internal error").asException()
    }
}
