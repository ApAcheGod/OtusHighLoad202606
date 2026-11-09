package ru.otus.highload.friend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.client.GrpcChannelFactory
import ru.otus.highload.grpc.user.v1.UserServiceGrpc

@Configuration
class GrpcClientConfig {

    @Bean
    fun userServiceStub(factory: GrpcChannelFactory): UserServiceGrpc.UserServiceBlockingStub =
        UserServiceGrpc.newBlockingStub(factory.createChannel("user-service"))
}
