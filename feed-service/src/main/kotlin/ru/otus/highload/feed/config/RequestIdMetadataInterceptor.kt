package ru.otus.highload.feed.config

import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.grpc.server.GlobalServerInterceptor
import org.springframework.stereotype.Component

@Component
@GlobalServerInterceptor
@Order(0)
class RequestIdMetadataInterceptor : ServerInterceptor {

    override fun <ReqT : Any, RespT : Any> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val requestId = headers.get(REQUEST_ID_KEY) ?: return next.startCall(call, headers)
        val delegate = next.startCall(call, headers)
        return object : ServerCall.Listener<ReqT>() {
            override fun onMessage(message: ReqT) = withMdc(requestId) { delegate.onMessage(message) }
            override fun onHalfClose() = withMdc(requestId) { delegate.onHalfClose() }
            override fun onCancel() = withMdc(requestId) { delegate.onCancel() }
            override fun onComplete() = withMdc(requestId) { delegate.onComplete() }
            override fun onReady() = withMdc(requestId) { delegate.onReady() }
        }
    }

    private inline fun withMdc(requestId: String, block: () -> Unit) {
        MDC.put(MDC_KEY, requestId)
        try {
            block()
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        const val MDC_KEY = "requestId"
        val REQUEST_ID_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER)
    }
}
