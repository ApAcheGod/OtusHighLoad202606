package ru.otus.highload.chat.config

import org.springframework.context.annotation.Configuration
import org.springframework.grpc.client.ImportGrpcClients
import ru.otus.highload.grpc.session.v1.SessionServiceGrpc

@Configuration
@ImportGrpcClients(types = [SessionServiceGrpc.SessionServiceBlockingStub::class])
class GrpcClientConfig
