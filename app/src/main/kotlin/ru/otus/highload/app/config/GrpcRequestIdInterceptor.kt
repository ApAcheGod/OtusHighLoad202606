package ru.otus.highload.app.config

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import org.slf4j.MDC
import org.springframework.grpc.client.GlobalClientInterceptor
import org.springframework.stereotype.Component

@Component
@GlobalClientInterceptor
class GrpcRequestIdInterceptor : ClientInterceptor {

    override fun <ReqT : Any, RespT : Any> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> =
        object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                MDC.get(RequestIdFilter.MDC_KEY)?.let {
                    headers.put(REQUEST_ID_KEY, it)
                }
                super.start(responseListener, headers)
            }
        }

    companion object {
        val REQUEST_ID_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER)
    }
}
