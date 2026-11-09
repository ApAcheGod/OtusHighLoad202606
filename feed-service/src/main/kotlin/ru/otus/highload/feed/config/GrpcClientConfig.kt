package ru.otus.highload.feed.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.client.GrpcChannelFactory
import ru.otus.highload.grpc.friend.v1.FriendServiceGrpc
import ru.otus.highload.grpc.post.v1.PostServiceGrpc

@Configuration
class GrpcClientConfig {

    @Bean
    fun postServiceStub(factory: GrpcChannelFactory): PostServiceGrpc.PostServiceBlockingStub =
        PostServiceGrpc.newBlockingStub(factory.createChannel("post-service"))

    @Bean
    fun friendServiceStub(factory: GrpcChannelFactory): FriendServiceGrpc.FriendServiceBlockingStub =
        FriendServiceGrpc.newBlockingStub(factory.createChannel("friend-service"))
}
